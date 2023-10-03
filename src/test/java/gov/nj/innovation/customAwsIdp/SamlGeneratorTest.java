package gov.nj.innovation.customAwsIdp;

import gov.nj.innovation.customAwsIdp.keys.KeyConstants;
import gov.nj.innovation.customAwsIdp.keys.KeysWrapper;
import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Date;

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
    private static KeysWrapper testKeys;

    @BeforeAll
    static void createTestKeys() {
        BigInteger modulus = new BigInteger(
                "22677974915050944078461764193052086685612422886653129966406898891640552702723187" +
                        "651748753893169403239949307486793794436232550013048860511799199448750673" +
                        "727894403945734106850876493539038073544416013323804855339770420391544025" +
                        "357955256202721629192822031612672259899947988772728037244868065127008714" +
                        "289841032060536668452506483479699611788091872105651219817275374428723167" +
                        "305362844116836623263360736859443098211386646756350982575062060545581019" +
                        "912311904126988936680434802991803415922815324589493813112228709634933389" +
                        "607199433744823421347161515965705700023418072722194325834553724568896465" +
                        "966048279285465688339476184785513");
        BigInteger publicExponent = new BigInteger("65537");
        BigInteger privateExponent = new BigInteger(
                "99181753208515446945833699464936667016550356438148730894324998013587338731068459" +
                        "964332920100549005960762168826804268280286779185652831899452912736288842" +
                        "291495392388208773305651387164911238379361884415453357061838301184465359" +
                        "540777351863910263933237939041796031155264847063234107074023973740465275" +
                        "086221307265585912988011409733023051220476097710594436933373486235030014" +
                        "916767387873109716406760851640646622463578188691241603750325393299313215" +
                        "006483194327973830956685111378221030131723154143951968586515181066678276" +
                        "013202252726969080222784005252041913912288730931292991576731109846173959" +
                        "4694582101837760154779049349473");
        BigInteger primeP = new BigInteger(
                "16349126722384686431275904713486430886977066508990251170284053900026494562204867" +
                        "744319187729543018975638715175328569037685704917934725308978647959772745" +
                        "016201300851097387256678052682712352135489528541546045176079720449868142" +
                        "196245805457065164389714138292369384957306371753647221290418447883615031" +
                        "7621062560601");
        BigInteger primeQ = new BigInteger(
                "13871061922837142681945035862349880189535194048901080097963915153419958450723403" +
                        "938501664730926349866288497831631534090822905014928653840261792938886833" +
                        "395086361820052176505799769325655382025211205011315226899385315797019091" +
                        "321268075560754410714529572114887432614341097438240009679910493898321014" +
                        "9633732877713");
        BigInteger primeExponentP = new BigInteger(
                "16348877258315193096420307479790755368859255546228620484546679835697946925075273" +
                        "700225861529263336612714326834159834909345413392461817871572160347462755" +
                        "655305681562743463558808808163544817577115854288398334019859934989434343" +
                        "454432841088762418381132883212974655729771432614355620405097325182730285" +
                        "5114118070273");
        BigInteger primeExponentQ = new BigInteger(
                "11009945071700952517998974938181155951286010333548763074537816841871810101899396" +
                        "516119414950914106439026219825497669589247550177328434992699974165554636" +
                        "104475295718713004404461574386243912867074502547928754567330282778356288" +
                        "988526237432212089216154444235230928439895166816299297550074980272163218" +
                        "9050416567217");
        BigInteger crtCoefficient = new BigInteger(
                "12184670836942564532594013718120505163607151281014131451689760816680375723185279" +
                        "763401514157576581602458430134504498914799297870407774134629646502863263" +
                        "864592534080775491236697368864656416266235661731507201672578304569609235" +
                        "831609240664824158520935430058898440871512892452119178554696789337898159" +
                        "6873692932103");
        X500Name crtSubject = new X500Name("CN=AwsConnectStandaloneIdP");
        BigInteger crtSerial = new BigInteger("1696169640002");
        Date crtNotBefore = new Date(1696169540000L);
        Date crtNotAfter = new Date(2011788840000L);
        String signatureAlgorithm = "SHA256WithRSA";

        KeyConstants keyConstants = new KeyConstants(
                modulus,
                publicExponent,
                privateExponent,
                primeP,
                primeQ,
                primeExponentP,
                primeExponentQ,
                crtCoefficient,
                crtSubject,
                crtSerial,
                crtNotBefore,
                crtNotAfter,
                signatureAlgorithm
        );
        testKeys = new KeysWrapper(keyConstants);
    }

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
        SamlGenerator samlGenerator = new SamlGenerator(TEST_USER, ROLE_NAME, DURATION, testKeys);
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
        String plaintext = Files.readAllLines(Path.of(VALIDATED_SAML_RESPONSE_FILEPATH), StandardCharsets.UTF_8).get(0);
        return Base64.getEncoder().encodeToString(plaintext.getBytes());
    }
}
