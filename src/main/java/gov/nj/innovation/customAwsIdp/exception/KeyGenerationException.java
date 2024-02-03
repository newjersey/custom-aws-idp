package gov.nj.innovation.customAwsIdp.exception;

/**
 * Specific exception for errors with key-generation while generating a SAML Response.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class KeyGenerationException extends CustomAwsIdpException {
    public KeyGenerationException(final String errorMessage, final Throwable err) {
        super(errorMessage, err);
    }
}
