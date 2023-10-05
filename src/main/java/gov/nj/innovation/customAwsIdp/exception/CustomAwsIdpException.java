package gov.nj.innovation.customAwsIdp.exception;

public class CustomAwsIdpException extends RuntimeException {
    public CustomAwsIdpException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
