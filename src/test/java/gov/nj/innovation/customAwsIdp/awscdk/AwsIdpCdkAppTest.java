package gov.nj.innovation.customAwsIdp.awscdk;

 import org.junit.jupiter.api.Assertions;
 import org.junit.jupiter.api.Test;
 import software.amazon.awscdk.App;
 import software.amazon.awscdk.assertions.Template;
 import java.util.Map;

public class AwsIdpCdkAppTest {
    private static final App app = new App();
    private static final AwsIdpCdkStack stack = new AwsIdpCdkStack(app, "test");
    private static final Template cdkTemplate = Template.fromStack(stack);

    @Test
    void stackHasTheOneExpectedLambda() {
        cdkTemplate.hasResourceProperties("AWS::Lambda::Function", Map.of(
                "FunctionName", "generateSamlResponse",
                "Handler", "gov.nj.innovation.customAwsIdp.lambda.GetSamlResponseHandler",
                "Runtime", "java17"));

        Assertions.assertEquals(1, cdkTemplate.findResources("AWS::Lambda::Function").size(),
                "There should only be the one generateSamlResponse lambda function");
    }
}
