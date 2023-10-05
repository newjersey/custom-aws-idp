package gov.nj.innovation.customAwsIdp.exception;

public class KeyGenerationException extends CustomAwsIdpException {
    public KeyGenerationException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
