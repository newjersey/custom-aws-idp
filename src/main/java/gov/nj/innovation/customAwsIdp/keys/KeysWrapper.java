package gov.nj.innovation.customAwsIdp.keys;

import gov.nj.innovation.customAwsIdp.exception.KeyGenerationException;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAMultiPrimePrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;

/**
 * House the {@link PublicKey}, {@link PrivateKey}, and {@link X509Certificate} used for signing a SAML Assertion. As
 * well, generate these members from the more basic elements contained in {@link KeyConstants}.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class KeysWrapper {

    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final X509Certificate x509Certificate;
    private static final String KEY_FACTORY_ALG = "RSA";

    public KeysWrapper(final KeyConstants keyConstants) {
        final KeyFactory keyFactory;
        try {
            keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALG);
        } catch (NoSuchAlgorithmException e) {
            throw new KeyGenerationException("Could not get key factory instance: " + e.getMessage(), e);
        }

        final KeySpec publicKeySpec = new RSAPublicKeySpec(keyConstants.modulus(), keyConstants.publicExponent());
        try {
            this.publicKey = keyFactory.generatePublic(publicKeySpec);
        } catch (InvalidKeySpecException e) {
            throw new KeyGenerationException("Could not construct public key: " + e.getMessage(), e);
        }

        final RSAMultiPrimePrivateCrtKeySpec privateKeySpec = new RSAMultiPrimePrivateCrtKeySpec(
                keyConstants.modulus(),
                keyConstants.publicExponent(),
                keyConstants.privateExponent(),
                keyConstants.primeP(),
                keyConstants.primeQ(),
                keyConstants.primeExponentP(),
                keyConstants.primeExponentQ(),
                keyConstants.crtCoefficient(),
                null
        );
        try {
            this.privateKey = keyFactory.generatePrivate(privateKeySpec);
        } catch (InvalidKeySpecException e) {
            throw new KeyGenerationException("Could not construct private key: " + e.getMessage(), e);
        }

        final X509v3CertificateBuilder certificateBuilder = new X509v3CertificateBuilder(
                keyConstants.certSubject(),
                keyConstants.certSerial(),
                keyConstants.certNotBefore(),
                keyConstants.certNotAfter(),
                keyConstants.certSubject(),
                SubjectPublicKeyInfo.getInstance(this.publicKey.getEncoded())
        );
        try {
            final ContentSigner signer = new JcaContentSignerBuilder(keyConstants.jcaSignerSignatureAlg())
                    .setProvider(new BouncyCastleProvider())
                    .build(this.privateKey);
            this.x509Certificate = new JcaX509CertificateConverter().getCertificate(certificateBuilder.build(signer));
        } catch (OperatorCreationException | CertificateException e) {
            throw new KeyGenerationException("Could not construct X509Certificate: " + e.getMessage(), e);
        }
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public X509Certificate getX509Certificate() {
        return x509Certificate;
    }
}
