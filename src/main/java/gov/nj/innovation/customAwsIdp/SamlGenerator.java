package gov.nj.innovation.customAwsIdp;

import gov.nj.innovation.customAwsIdp.exception.CustomAwsIdpException;
import gov.nj.innovation.customAwsIdp.keys.KeysWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.VisibleForTesting;
import org.keycloak.dom.saml.v2.assertion.AssertionType;
import org.keycloak.dom.saml.v2.assertion.AttributeStatementType;
import org.keycloak.dom.saml.v2.assertion.AttributeType;
import org.keycloak.dom.saml.v2.protocol.ResponseType;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.saml.JaxrsSAML2BindingBuilder;
import org.keycloak.protocol.saml.SamlProtocol;
import org.keycloak.protocol.saml.mappers.AttributeStatementHelper;
import org.keycloak.protocol.saml.mappers.HardcodedAttributeMapper;
import org.keycloak.protocol.saml.mappers.RoleListMapper;
import org.keycloak.protocol.saml.mappers.SAMLAttributeStatementMapper;
import org.keycloak.protocol.saml.mappers.SAMLRoleListMapper;
import org.keycloak.saml.BaseSAML2BindingBuilder;
import org.keycloak.saml.SAML2LoginResponseBuilder;
import org.keycloak.saml.SignatureAlgorithm;
import org.keycloak.saml.common.constants.JBossSAMLURIConstants;
import org.keycloak.saml.common.exceptions.ConfigurationException;
import org.keycloak.saml.common.exceptions.ProcessingException;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.w3c.dom.Document;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generate a Base64-encoded SAMLResponse intended to be used for federating SSO log-ins to AWS Connect.
 * <p>
 * This class was constructed by debugging a correctly-configured live Keycloak Quarkus server and then ripping out the
 * necessary parts in order to generate SAMLResponses without the weight of having to run a full Keycloak server. The
 * logic inside this class's methods was copied directly from Keycloak, then simplified and cut down where feasible. It
 * could likely be cleaned up and simplified more, but it is being left as-is on purpose so that it may more-closely
 * resemble the code it was copied from. Hopefully if the source code eventually has breaking changes which must be
 * consumed here for security concerns, this code's clumsiness will actually make it easier to refactor.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class SamlGenerator {

    /* Constants */
    private static final Logger logger = LogManager.getLogger(SamlGenerator.class);
    private static final String REDIRECT_URI = "https://signin.aws.amazon.com/saml";
    private static final String ISSUER = "https://innovation.nj.gov/realms/AwsConnectStandaloneIdP";
    private static final String NAME_ID_FORMAT = "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified";
    private static final String SESSION_ROLE_ATTR_NAME = "https://aws.amazon.com/SAML/Attributes/Role";
    private static final String SESSION_ROLE_NAME = "Session Role";
    private static final String SESSION_NAME_ATTR_NAME = "https://aws.amazon.com/SAML/Attributes/RoleSessionName";
    private static final String SESSION_NAME_NAME = "Session Name";
    private static final String SESSION_DURATION_ATTR_NAME = "https://aws.amazon.com/SAML/Attributes/SessionDuration";
    private static final String SESSION_DURATION_NAME = "Session Duration";
    private static final String BASIC_NAME_FORMAT = "Basic";
    private static final int ASSERTION_EXPIRATION = 60;
    private static final int SUBJECT_EXPIRATION = 300;
    private static final int SESSION_EXPIRATION = 36000;
    private static final String CLIENT_ID = "urn:amazon:webservices";
    private static final String CANONICALIZATION_ALG = "http://www.w3.org/2001/10/xml-exc-c14n#";
    private static final SignatureAlgorithm SIGNATURE_ALG = SignatureAlgorithm.RSA_SHA256;

    /* Instance variables */
    private final String user;
    private final String roleName;
    private final String duration;
    private final KeysWrapper keys;

    public SamlGenerator(final String user, final String roleName, final String duration, final KeysWrapper keys) {
        this.user = user;
        this.roleName = roleName;
        this.duration = duration;
        this.keys = keys;
    }

    /**
     * Put together the two methods taken from {@link SamlProtocol}, this class's {@link #createUnsignedSamlResponse()} and {@link
     * #signAndEncode(Document)}, in order to create a full SAML Response. Although this came from Keycloak, it is
     * now purpose-built to generate one SAML Response with one Role as well as the attributes necessary to federate a
     * sign-on to AWS Connect.
     *
     * @return The Base64-encoded, signed SAML Response.
     */
    public String getBase64SamlResponse() {
        Document samlDocument = createUnsignedSamlResponse();
        return signAndEncode(samlDocument);
    }

    /**
     * This has copied the logic almost entirely from {@link SamlProtocol#authenticated(AuthenticationSessionModel,
     * UserSessionModel, ClientSessionContext)} while cutting things down as much as possible and removing dependencies
     * on sessions, users, clients, and key stores; instead providing instance variables and constants.
     * <p>
     * Unused chunks of logic have been removed, unnecessary complexity for other use-cases has been simplified where
     * feasible.
     *
     * @return The unsigned XML {@link Document} which will become the SAML Response.
     */
    @VisibleForTesting
    Document createUnsignedSamlResponse() {
        SAML2LoginResponseBuilder builder = new SAML2LoginResponseBuilder();
        builder.requestID(null)
                .destination(REDIRECT_URI)
                .issuer(ISSUER)
                .assertionExpiration(ASSERTION_EXPIRATION)
                .subjectExpiration(SUBJECT_EXPIRATION)
                .sessionExpiration(SESSION_EXPIRATION)
                .requestIssuer(CLIENT_ID)
                .authMethod(JBossSAMLURIConstants.AC_UNSPECIFIED.get())
                .includeOneTimeUseCondition(false);

        List<SamlProtocol.ProtocolMapperProcessor<SAMLAttributeStatementMapper>> attributeStatementMappers =
                new LinkedList<>();
        AtomicReference<SamlProtocol.ProtocolMapperProcessor<SAMLRoleListMapper>> roleListMapper =
                new AtomicReference<>(null);

        ProtocolMapperModel roleMapping = RoleListMapper
                .create(SESSION_ROLE_NAME, SESSION_ROLE_ATTR_NAME, BASIC_NAME_FORMAT, null, true);
        roleListMapper.set(new SamlProtocol.ProtocolMapperProcessor<>(new RoleListMapper(), roleMapping));

        ProtocolMapperModel nameMapping = HardcodedAttributeMapper
                .create(SESSION_NAME_NAME, SESSION_NAME_ATTR_NAME, BASIC_NAME_FORMAT, null, user);
        attributeStatementMappers
                .add(new SamlProtocol.ProtocolMapperProcessor<>(new HardcodedAttributeMapper(), nameMapping));

        ProtocolMapperModel durationMapping = HardcodedAttributeMapper
                .create(SESSION_DURATION_NAME, SESSION_DURATION_ATTR_NAME, BASIC_NAME_FORMAT, null, duration);
        attributeStatementMappers
                .add(new SamlProtocol.ProtocolMapperProcessor<>(new HardcodedAttributeMapper(), durationMapping));

        builder.nameIdentifier(NAME_ID_FORMAT, user);

        ResponseType samlModel;
        try {
            samlModel = builder.buildModel();
            final AttributeStatementType attributeStatement =
                    populateAttributeStatements(attributeStatementMappers);
            populateRoles(roleListMapper.get(), attributeStatement);

            if (attributeStatement.getAttributes().size() > 0) {
                AssertionType assertion = samlModel.getAssertions().get(0).getAssertion();
                assertion.addStatement(attributeStatement);
            }
        } catch (ConfigurationException | ProcessingException e) {
            throw new CustomAwsIdpException("Could not build role/attribute statement(s): " + e.getMessage(), e);
        }

        try {
            return builder.buildDocument(samlModel);
        } catch (ConfigurationException | ProcessingException e) {
            throw new CustomAwsIdpException("Could not create the document: " + e.getMessage(), e);
        }
    }

    /**
     * Follow up on the results from {@link #createUnsignedSamlResponse()} to sign and encode the document.
     *
     * @param samlDocument The fully constructed SAML Document, ready to be signed
     * @return The Base64-encoded, signed SAML Response.
     */
    @VisibleForTesting
    String signAndEncode(Document samlDocument) {
        JaxrsSAML2BindingBuilder bindingBuilder = new JaxrsSAML2BindingBuilder(null);

        bindingBuilder.canonicalizationMethod(CANONICALIZATION_ALG);
        bindingBuilder.signatureAlgorithm(SIGNATURE_ALG)
                .signWith(null, keys.getPrivateKey(), keys.getPublicKey(), keys.getX509Certificate());
        bindingBuilder.signAssertions();

        try {
            // Go a couple steps further than SamlProtocol#authenticated to make the signed + encoded XML string
            bindingBuilder.postBinding(samlDocument);
            String base64SamlResponse = BaseSAML2BindingBuilder.getSAMLResponse(samlDocument);
            logger.trace("Generated SAMLResponse for " + user + " with role " + roleName + " for " + duration + " secs");
            return base64SamlResponse;
        } catch (ProcessingException | ConfigurationException | IOException e) {
            throw new CustomAwsIdpException("Could not sign the document: " + e.getMessage(), e);
        }
    }

    /**
     * This has copied the logic almost entirely from {@link SamlProtocol#populateAttributeStatements(List,
     * KeycloakSession, UserSessionModel, AuthenticatedClientSessionModel)} where the session, user, and client are
     * dropped entirely.
     *
     * @return The attribute statement.
     */
    private AttributeStatementType populateAttributeStatements(
            List<SamlProtocol.ProtocolMapperProcessor<SAMLAttributeStatementMapper>> attributeStatementMappers
    ) {
        AttributeStatementType attributeStatement = new AttributeStatementType();
        for (SamlProtocol.ProtocolMapperProcessor<SAMLAttributeStatementMapper> processor : attributeStatementMappers) {
            processor.mapper.transformAttributeStatement(attributeStatement, processor.model, null, null, null);
        }
        return attributeStatement;
    }

    /**
     * This has copied the logic almost entirely from {@link SamlProtocol#populateRoles(
     *SamlProtocol.ProtocolMapperProcessor, KeycloakSession, UserSessionModel, ClientSessionContext,
     * AttributeStatementType)} where the session, user, and client are dropped entirely.
     */
    private void populateRoles(
            SamlProtocol.ProtocolMapperProcessor<SAMLRoleListMapper> roleListMapper,
            final AttributeStatementType existingAttributeStatement
    ) {
        if (roleListMapper == null)
            return;
        localMapRoles(existingAttributeStatement, roleListMapper.model);
    }

    /**
     * This has copied the logic almost entirely from {@link RoleListMapper#mapRoles(AttributeStatementType,
     * ProtocolMapperModel, KeycloakSession, UserSessionModel, ClientSessionContext)} where the session, user, and
     * client are dropped entirely. This one was heavily cut down because most of the code wasn't needed.
     */
    private void localMapRoles(AttributeStatementType roleAttributeStatement, ProtocolMapperModel mappingModel) {
        AtomicReference<AttributeType> singleAttributeType = new AtomicReference<>(null);
        AttributeType attributeType;
        singleAttributeType.set(AttributeStatementHelper.createAttributeType(mappingModel));
        roleAttributeStatement.addAttribute(new AttributeStatementType.ASTChoiceType(singleAttributeType.get()));
        attributeType = singleAttributeType.get();
        attributeType.addAttributeValue(roleName);
    }
}
