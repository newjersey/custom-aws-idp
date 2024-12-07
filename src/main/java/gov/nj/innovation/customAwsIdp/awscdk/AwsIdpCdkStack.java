package gov.nj.innovation.customAwsIdp.awscdk;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.aws_apigatewayv2_authorizers.HttpJwtAuthorizer;
import software.amazon.awscdk.aws_apigatewayv2_integrations.HttpLambdaIntegration;
import software.amazon.awscdk.services.apigatewayv2.AddRoutesOptions;
import software.amazon.awscdk.services.apigatewayv2.CorsHttpMethod;
import software.amazon.awscdk.services.apigatewayv2.CorsPreflightOptions;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.apigatewayv2.PayloadFormatVersion;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awssdk.regions.Region;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class AwsIdpCdkStack extends Stack {

    static final String AWS_ACCOUNT_ID = "274460373520";
    private static final String AWS_REGION = Region.US_EAST_1.id();
    private static final String COGNITO_APP_CLIENT_ID = "7i01fral9t0fdtodp78hi3vqrh";
    private static final String COGNITO_USER_POOL = "us-east-1_AZyvZQdFN";
    private static final String PATH_PARAMETER_GROUP_NAME = "groupName";
    private static final String KEY_PRIVATE_EXPONENT_NAME = "custom-aws-idp-private-key-private-exponent";
    private static final String KEY_PRIME_P_NAME = "custom-aws-idp-private-key-prime-p";
    private static final String KEY_PRIME_Q_NAME = "custom-aws-idp-private-key-prime-q";
    private static final String KEY_PRIME_EXPONENT_P_NAME = "custom-aws-idp-private-key-prime-exponent-p";
    private static final String KEY_PRIME_EXPONENT_Q_NAME = "custom-aws-idp-private-key-prime-exponent-q";
    private static final String KEY_CRT_COEFFICIENT_NAME = "custom-aws-idp-private-key-crt-coefficient";
    private static final String URL_PATH = "generateSaml/{" + PATH_PARAMETER_GROUP_NAME + "}";

    public AwsIdpCdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public AwsIdpCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Create the HTTP API and Cognito Authorizer
        final HttpApi httpApi = HttpApi.Builder.create(this, "HttpApi")
                .corsPreflight(CorsPreflightOptions.builder()
                        .allowHeaders(List.of(
                                "Content-Type",
                                "X-Amz-Date",
                                "Authorization",
                                "X-Api-Key",
                                "X-Amz-Security-Token",
                                "X-Amz-User-Agent",
                                "X-Amzn-Trace-Id"))
                        .allowMethods(List.of(CorsHttpMethod.OPTIONS, CorsHttpMethod.GET))
                        .allowOrigins(List.of("*"))
                        .build())
                .build();

        final String jwtIssuer = MessageFormat.format("https://cognito-idp.{0}.amazonaws.com/{1}",
                AWS_REGION, COGNITO_USER_POOL);
        final HttpJwtAuthorizer cognitoAuthorizer = HttpJwtAuthorizer.Builder
                .create("CognitoAuthorizer", jwtIssuer)
                .authorizerName("CognitoAuthorizer")
                .jwtAudience(List.of(COGNITO_APP_CLIENT_ID))
                .identitySource(List.of("$request.header.Authorization"))
                .build();

        // Create the Log Group and Lambda
        final String logGroupId = "GenerateSamlResponseLogGroup";
        final String logGroupName = "/aws/lambda/generateSamlResponse";
        final LogGroup lambdaLogGroup = LogGroup.Builder.create(this, logGroupId).logGroupName(logGroupName).build();

        final Function generateSamlResponse = Function.Builder.create(this, "GenerateSamlResponseLambda")
                .functionName("generateSamlResponse")
                .description("Generate a SAML Response for logging in to Amazon Connect")
                .runtime(Runtime.JAVA_17)
                .code(Code.fromAsset("build/distributions/customIdp.zip"))
                .handler("gov.nj.innovation.customAwsIdp.lambda.GetSamlResponseHandler")
                .logGroup(lambdaLogGroup)
                .environment(Map.ofEntries(
                        Map.entry("COGNITO_REGION", AWS_REGION),
                        Map.entry("COGNITO_USER_POOL", COGNITO_USER_POOL),
                        Map.entry("PATH_PARAMETER_GROUP_NAME", PATH_PARAMETER_GROUP_NAME),
                        // Get all secret key details from SSM
                        Map.entry("KEY_PRIVATE_EXPONENT_NAME", KEY_PRIVATE_EXPONENT_NAME),
                        Map.entry("KEY_PRIME_P_NAME", KEY_PRIME_P_NAME),
                        Map.entry("KEY_PRIME_Q_NAME", KEY_PRIME_Q_NAME),
                        Map.entry("KEY_PRIME_EXPONENT_P_NAME", KEY_PRIME_EXPONENT_P_NAME),
                        Map.entry("KEY_PRIME_EXPONENT_Q_NAME", KEY_PRIME_EXPONENT_Q_NAME),
                        Map.entry("KEY_CRT_COEFFICIENT_NAME", KEY_CRT_COEFFICIENT_NAME),
                        // Set a default session duration of 1 hour
                        Map.entry("DEFAULT_SESSION_DURATION", "3600")
                ))
                .build();

        // Connect the Lambda to the authorizer
        final HttpLambdaIntegration lambdaIntegration = HttpLambdaIntegration.Builder
                .create("AuthorizerIntegration", generateSamlResponse)
                .payloadFormatVersion(PayloadFormatVersion.VERSION_2_0)
                .build();

        httpApi.addRoutes(AddRoutesOptions.builder()
                .path("/" + URL_PATH)
                .authorizer(cognitoAuthorizer)
                .methods(List.of(software.amazon.awscdk.services.apigatewayv2.HttpMethod.GET))
                .integration(lambdaIntegration)
                .build()
        );

        // Add the role/policy for the Lambda
        final String userPoolArn = MessageFormat.format("arn:aws:cognito-idp:{0}:{1}:userpool/{2}",
                AWS_REGION, AWS_ACCOUNT_ID, COGNITO_USER_POOL);
        final List<String> ssmParameterArnList = Stream.of(
                        KEY_PRIVATE_EXPONENT_NAME,
                        KEY_PRIME_P_NAME,
                        KEY_PRIME_Q_NAME,
                        KEY_PRIME_EXPONENT_P_NAME,
                        KEY_PRIME_EXPONENT_Q_NAME,
                        KEY_CRT_COEFFICIENT_NAME)
                .map((key) -> MessageFormat.format("arn:aws:ssm:{0}:{1}:parameter/{2}",
                        AWS_REGION, AWS_ACCOUNT_ID, key))
                .toList();
        final String logGroupBaseArn = MessageFormat.format("arn:aws:logs:{0}:{1}:log-group:{2}*:*",
                AWS_REGION, AWS_ACCOUNT_ID, logGroupName);

        generateSamlResponse.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("logs:CreateLogStream", "logs:CreateLogGroup", "logs:TagResource"))
                .resources(List.of(logGroupBaseArn))
                .build());
        generateSamlResponse.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("logs:PutLogEvents"))
                .resources(List.of(logGroupBaseArn + ":*"))
                .build());
        generateSamlResponse.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("cognito-idp:GetGroup"))
                .resources(List.of(userPoolArn))
                .build());
        generateSamlResponse.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("ssm:GetParameter"))
                .resources(ssmParameterArnList)
                .build());

        // Output
        CfnOutput.Builder.create(this, "ApiUrlOutput")
                .key("ApiUrl")
                .exportName("ApiUrl")
                .value(httpApi.getUrl() + URL_PATH)
                .build();
    }
}
