package gov.nj.innovation.customAwsIdp.awscdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awssdk.regions.Region;

import static gov.nj.innovation.customAwsIdp.awscdk.AwsIdpCdkStack.AWS_ACCOUNT_ID;

public class AwsIdpCdkApp {
    public static void main(final String[] args) {
        App app = new App();

        new AwsIdpCdkStack(app, "CustomAwsIdpCdkStack", StackProps.builder()
                .env(Environment.builder()
                        .account(AWS_ACCOUNT_ID)
                        .region(Region.US_EAST_1.id())
                        .build())
                .build());

        app.synth();
    }
}

