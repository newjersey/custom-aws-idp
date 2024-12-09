package gov.nj.innovation.customAwsIdp.util;

import software.amazon.awssdk.regions.Region;

/**
 * Constants. Some shared between the infrastructure and the Lambda.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class Constants {
    public static final String AWS_ACCOUNT_ID = "274460373520";
    public static final String AWS_REGION = Region.US_EAST_1.id();
    public static final String COGNITO_APP_CLIENT_ID = "7i01fral9t0fdtodp78hi3vqrh";
    public static final String COGNITO_USER_POOL = "us-east-1_AZyvZQdFN";
    public static final String DEFAULT_SESSION_DURATION = "3600";
    public static final String KEY_PRIVATE_EXPONENT_NAME = "custom-aws-idp-private-key-private-exponent";
    public static final String KEY_PRIME_P_NAME = "custom-aws-idp-private-key-prime-p";
    public static final String KEY_PRIME_Q_NAME = "custom-aws-idp-private-key-prime-q";
    public static final String KEY_PRIME_EXPONENT_P_NAME = "custom-aws-idp-private-key-prime-exponent-p";
    public static final String KEY_PRIME_EXPONENT_Q_NAME = "custom-aws-idp-private-key-prime-exponent-q";
    public static final String KEY_CRT_COEFFICIENT_NAME = "custom-aws-idp-private-key-crt-coefficient";
    public static final String PATH_PARAMETER_GROUP_NAME = "groupName";
}
