package gov.nj.innovation.customAwsIdp.exception;

/**
 * Exception for general errors while generating a SAML Response.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class CustomAwsIdpException extends RuntimeException {
    public CustomAwsIdpException(final String errorMessage, final Throwable err) {
        super(errorMessage, err);
    }
}
