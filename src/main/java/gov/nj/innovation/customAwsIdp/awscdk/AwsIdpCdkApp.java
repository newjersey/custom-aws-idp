package gov.nj.innovation.customAwsIdp.awscdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

import static gov.nj.innovation.customAwsIdp.util.Constants.AWS_ACCOUNT_ID;
import static gov.nj.innovation.customAwsIdp.util.Constants.AWS_REGION;

/**
 * App for deploying infrastructure using the AWS CDK.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class AwsIdpCdkApp {
    public static void main(final String[] args) {
        App app = new App();

        new AwsIdpCdkStack(app, "CustomAwsIdpCdkStack", StackProps.builder()
                .env(Environment.builder()
                        .account(AWS_ACCOUNT_ID)
                        .region(AWS_REGION)
                        .build())
                .build());

        app.synth();
    }
}

