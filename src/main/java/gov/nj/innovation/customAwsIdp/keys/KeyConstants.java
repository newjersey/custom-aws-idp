package gov.nj.innovation.customAwsIdp.keys;

import gov.nj.innovation.customAwsIdp.util.SsmClientWrapper;
import org.bouncycastle.asn1.x500.X500Name;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.math.BigInteger;
import java.util.Date;

/**
 * Store the constants needed for generating keys; some which are public, and some which are secrets populated from
 * environment variables and SSM.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public record KeyConstants(
        BigInteger modulus,
        BigInteger publicExponent,
        BigInteger privateExponent,
        BigInteger primeP,
        BigInteger primeQ,
        BigInteger primeExponentP,
        BigInteger primeExponentQ,
        BigInteger crtCoefficient,
        X500Name certSubject,
        BigInteger certSerial,
        Date certNotBefore,
        Date certNotAfter,
        String jcaSignerSignatureAlg
        ) {

    private static final BigInteger KEY_MODULUS = new BigInteger(
            "186207792853874271571279459245893970033952627841455523147130859272884847880052280823" +
                    "9569494436513647099329450690108068407106345868752865353856531579488503178218" +
                    "5915972217579869940563691599030649406130191546150630364063192500449708934462" +
                    "9627298190613541869108251398962357460408138626841628759181065577547704564137" +
                    "2876147025464781569143101414272935598172769429909845933379294445552939069387" +
                    "8897800453375015283804978854638032254433921291138591065482714913699271839322" +
                    "2538010532882824562974521623107950688203553158025280423089593531967242884220" +
                    "99768495627357285435626145610679202888676240909221166688413303345247117760811"
    );
    private static final BigInteger KEY_PUBLIC_EXPONENT = new BigInteger("65537");
    private static final X500Name CERT_SUBJECT = new X500Name("CN=AwsConnectStandaloneIdP");
    private static final BigInteger CERT_SERIAL = new BigInteger("1696019667843");
    private static final Date CERT_NOT_BEFORE = new Date(1696019567000L);
    private static final Date CERT_NOT_AFTER = new Date(2011638867000L);
    private static final String JCA_SIGNER_SIGNATURE_ALG = "SHA256WithRSA";

    // Names defined in the environment
    private static final String KEY_PRIVATE_EXPONENT_NAME = "KEY_PRIVATE_EXPONENT_NAME";
    private static final String KEY_PRIME_P_NAME = "KEY_PRIME_P_NAME";
    private static final String KEY_PRIME_Q_NAME = "KEY_PRIME_Q_NAME";
    private static final String KEY_PRIME_EXPONENT_P_NAME = "KEY_PRIME_EXPONENT_P_NAME";
    private static final String KEY_PRIME_EXPONENT_Q_NAME = "KEY_PRIME_EXPONENT_Q_NAME";
    private static final String KEY_CRT_COEFFICIENT_NAME = "KEY_CRT_COEFFICIENT_NAME";

    public KeyConstants(final SsmClient ssmClient) {
        this(
                KEY_MODULUS,
                KEY_PUBLIC_EXPONENT,
                new BigInteger(SsmClientWrapper.getParameterByName(ssmClient, System.getenv(KEY_PRIVATE_EXPONENT_NAME))),
                new BigInteger(SsmClientWrapper.getParameterByName(ssmClient, System.getenv(KEY_PRIME_P_NAME))),
                new BigInteger(SsmClientWrapper.getParameterByName(ssmClient, System.getenv(KEY_PRIME_Q_NAME))),
                new BigInteger(SsmClientWrapper.getParameterByName(ssmClient, System.getenv(KEY_PRIME_EXPONENT_P_NAME))),
                new BigInteger(SsmClientWrapper.getParameterByName(ssmClient, System.getenv(KEY_PRIME_EXPONENT_Q_NAME))),
                new BigInteger(SsmClientWrapper.getParameterByName(ssmClient, System.getenv(KEY_CRT_COEFFICIENT_NAME))),
                CERT_SUBJECT,
                CERT_SERIAL,
                CERT_NOT_BEFORE,
                CERT_NOT_AFTER,
                JCA_SIGNER_SIGNATURE_ALG
        );
    }
}
