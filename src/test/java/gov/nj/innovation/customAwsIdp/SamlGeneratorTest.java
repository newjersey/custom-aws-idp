package gov.nj.innovation.customAwsIdp;

import gov.nj.innovation.customAwsIdp.keys.KeyConstants;
import gov.nj.innovation.customAwsIdp.keys.KeysWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.stream.Collectors;

import static gov.nj.innovation.customAwsIdp.TestUtils.TestKeyDetails.CERT_NOT_AFTER;
import static gov.nj.innovation.customAwsIdp.TestUtils.TestKeyDetails.CERT_NOT_BEFORE;
import static gov.nj.innovation.customAwsIdp.TestUtils.TestKeyDetails.CERT_SERIAL;
import static gov.nj.innovation.customAwsIdp.TestUtils.TestKeyDetails.CERT_SUBJECT;
import static gov.nj.innovation.customAwsIdp.TestUtils.TestKeyDetails.CRT_COEFFICIENT;
import static gov.nj.innovation.customAwsIdp.TestUtils.TestKeyDetails.MODULUS;
import static gov.nj.innovation.customAwsIdp.TestUtils.TestKeyDetails.PRIME_EXPONENT_P;
import static gov.nj.innovation.customAwsIdp.TestUtils.TestKeyDetails.PRIME_EXPONENT_Q;
import static gov.nj.innovation.customAwsIdp.TestUtils.TestKeyDetails.PRIME_P;
import static gov.nj.innovation.customAwsIdp.TestUtils.TestKeyDetails.PRIME_Q;
import static gov.nj.innovation.customAwsIdp.TestUtils.TestKeyDetails.PRIVATE_EXPONENT;
import static gov.nj.innovation.customAwsIdp.TestUtils.TestKeyDetails.PUBLIC_EXPONENT;
import static gov.nj.innovation.customAwsIdp.TestUtils.TestKeyDetails.SIGNATURE_ALGORITHM;

/**
 * Tests for {@link SamlGenerator}.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class SamlGeneratorTest {

    private static final String TEST_USER = "test@test.com";
    private static final String ROLE_NAME =
            "arn:aws:iam::274460373520:role/TempForTesting,arn:aws:iam::274460373520:saml-provider/TempForTesting";
    private static final String DURATION = "901";
    private static final String RESPONSE_ISSUE_INSTANT = "2023-10-01T22:00:01.066Z";
    private static final String RESPONSE_ID = "ID_c97c72f7-67ab-4d7f-bc21-33e5414c5e2b";
    private static final String ASSERTION_ISSUE_INSTANT = "2023-10-01T22:00:01.058Z";
    private static final String ASSERTION_ID = "ID_78f07358-7010-42c2-a4cc-3f2637b4e148";
    private static final String SUBJECT_CONF_DATA_NOT_ON_OR_AFTER = "2023-10-01T22:04:59.058Z";
    private static final String CONDITIONS_NOT_BEFORE = "2023-10-01T21:59:59.058Z";
    private static final String CONDITIONS_NOT_ON_OR_AFTER = "2023-10-01T22:00:59.058Z";
    private static final String AUTHN_STATEMENT_AUTHN_INSTANT = "2023-10-01T22:00:01.068Z";
    private static final String AUTHN_STATEMENT_SESSION_NOT_ON_OR_AFTER = "2023-10-02T08:00:01.068Z";
    private static final String AUTHN_STATEMENT_SESSION_INDEX = ASSERTION_ID;
    private static final String VALIDATED_SAML_RESPONSE_FILEPATH = "./src/test/resources/validatedSignedSamlResponse.xml";
    private static final KeysWrapper TEST_KEYS = new KeysWrapper(
            new KeyConstants(
                    MODULUS,
                    PUBLIC_EXPONENT,
                    PRIVATE_EXPONENT,
                    PRIME_P,
                    PRIME_Q,
                    PRIME_EXPONENT_P,
                    PRIME_EXPONENT_Q,
                    CRT_COEFFICIENT,
                    CERT_SUBJECT,
                    CERT_SERIAL,
                    CERT_NOT_BEFORE,
                    CERT_NOT_AFTER,
                    SIGNATURE_ALGORITHM
            ));

    /**
     * One big test which:
     * <ul>
     *   <li>Runs the full SamlGenerator code</li>
     *   <li>Forcibly replaces the SAML values which change each time (IDs and Timestamps) with values from the
     *       validated example SAML</li>
     *   <li>Uses the test-keys (generated above) to sign this SAML Response
     *   <li>Generates the base64-encoded SAML Response</li>
     *   <li>Asserts that the generated SAML Response contains identical data and an identical signature to the
     *       validated example SAML, thus proving that the code can generate a SAML Response which has been seen to
     *       work in AWS itself</li>
     * </ul>
     * @throws IOException If there are issues reading the example SAML file.
     */
    @Test
    @DisplayName("Test that the SamlGenerator can produce a SAMLResponse that matches one validated to work in AWS")
    void testFullSamlResponse() throws IOException {
        SamlGenerator samlGenerator = new SamlGenerator(TEST_USER, ROLE_NAME, DURATION, TEST_KEYS);
        Document samlDocument = samlGenerator.createUnsignedSamlResponse();
        replaceTimestampsAndIds(samlDocument);
        String base64Encoded = samlGenerator.signAndEncode(samlDocument);
        Assertions.assertEquals(readAndEncodeExpectedSignedSaml(), base64Encoded);
    }

    /**
     * Replace the timestamps and IDs in the generated (not yet signed) SAML Response so that it can match the sample
     * document when it does get signed.
     */
    private void replaceTimestampsAndIds(final Document samlDocument) {
        NamedNodeMap responseAttrs = samlDocument
                .getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:protocol", "Response")
                .item(0).getAttributes();
        responseAttrs.getNamedItem("IssueInstant").setNodeValue(RESPONSE_ISSUE_INSTANT);
        responseAttrs.getNamedItem("ID").setNodeValue(RESPONSE_ID);

        NamedNodeMap assertionAttrs = samlDocument
                .getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Assertion")
                .item(0).getAttributes();
        assertionAttrs.getNamedItem("IssueInstant").setNodeValue(ASSERTION_ISSUE_INSTANT);
        assertionAttrs.getNamedItem("ID").setNodeValue(ASSERTION_ID);

        NamedNodeMap subjectConfirmationDataAttrs = samlDocument
                .getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "SubjectConfirmationData")
                .item(0).getAttributes();
        subjectConfirmationDataAttrs.getNamedItem("NotOnOrAfter").setNodeValue(SUBJECT_CONF_DATA_NOT_ON_OR_AFTER);

        NamedNodeMap conditionsAttrs = samlDocument
                .getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Conditions")
                .item(0).getAttributes();
        conditionsAttrs.getNamedItem("NotBefore").setNodeValue(CONDITIONS_NOT_BEFORE);
        conditionsAttrs.getNamedItem("NotOnOrAfter").setNodeValue(CONDITIONS_NOT_ON_OR_AFTER);

        NamedNodeMap authnStatementAttrs = samlDocument
                .getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "AuthnStatement")
                .item(0).getAttributes();
        authnStatementAttrs.getNamedItem("AuthnInstant").setNodeValue(AUTHN_STATEMENT_AUTHN_INSTANT);
        authnStatementAttrs.getNamedItem("SessionNotOnOrAfter").setNodeValue(AUTHN_STATEMENT_SESSION_NOT_ON_OR_AFTER);
        authnStatementAttrs.getNamedItem("SessionIndex").setNodeValue(AUTHN_STATEMENT_SESSION_INDEX);
    }

    private String readAndEncodeExpectedSignedSaml() throws IOException {
        String plaintext = Files.readAllLines(Path.of(VALIDATED_SAML_RESPONSE_FILEPATH), StandardCharsets.UTF_8)
                .stream()
                .map(line -> line.replaceAll("^\\s*", ""))
                .map(line -> line.replaceAll("\\n", ""))
                .collect(Collectors.joining());
        return Base64.getEncoder().encodeToString(plaintext.getBytes());
    }
}
