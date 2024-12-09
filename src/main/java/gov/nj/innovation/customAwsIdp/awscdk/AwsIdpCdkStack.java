package gov.nj.innovation.customAwsIdp.awscdk;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
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
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import java.text.MessageFormat;
import java.util.List;
import java.util.stream.Stream;

import static gov.nj.innovation.customAwsIdp.util.Constants.AWS_ACCOUNT_ID;
import static gov.nj.innovation.customAwsIdp.util.Constants.AWS_REGION;
import static gov.nj.innovation.customAwsIdp.util.Constants.COGNITO_APP_CLIENT_ID;
import static gov.nj.innovation.customAwsIdp.util.Constants.COGNITO_USER_POOL;
import static gov.nj.innovation.customAwsIdp.util.Constants.KEY_CRT_COEFFICIENT_NAME;
import static gov.nj.innovation.customAwsIdp.util.Constants.KEY_PRIME_EXPONENT_P_NAME;
import static gov.nj.innovation.customAwsIdp.util.Constants.KEY_PRIME_EXPONENT_Q_NAME;
import static gov.nj.innovation.customAwsIdp.util.Constants.KEY_PRIME_P_NAME;
import static gov.nj.innovation.customAwsIdp.util.Constants.KEY_PRIME_Q_NAME;
import static gov.nj.innovation.customAwsIdp.util.Constants.KEY_PRIVATE_EXPONENT_NAME;
import static gov.nj.innovation.customAwsIdp.util.Constants.PATH_PARAMETER_GROUP_NAME;

/**
 * Stack for deploying infrastructure using the AWS CDK.
 * <p>
 * This stack currently includes:
 * <ul>
 *     <li>An HTTP API</li>
 *     <li>A JWT Authorizer</li>
 *     <li>A custom log group</li>
 *     <li>The Lambda function</li>
 *     <li>An HTTP Lambda Integration, to connect the APIGateway Route to the HTTP API</li>
 *     <li>The APIGateway Authorizer/Lambda route</li>
 *     <li>All the required permissions for the Lambda role</li>
 * </ul>
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class AwsIdpCdkStack extends Stack {

    private static final String URL_PATH = "generateSaml/{" + PATH_PARAMETER_GROUP_NAME + "}";

    public AwsIdpCdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public AwsIdpCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

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
                .memorySize(1024)
                .timeout(Duration.seconds(15))
                .build();

        // Create the HTTP API, the Cognito Authorizer, and the parts required to connect the Lambda to the Authorizer
        final HttpApi httpApi = HttpApi.Builder.create(this, "HttpApi-for-GenerateSamlResponseLambda")
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

        // Output the HTTP API URL
        CfnOutput.Builder.create(this, "ApiUrlOutput")
                .key("ApiUrl")
                .exportName("ApiUrl")
                .value(httpApi.getUrl() + URL_PATH)
                .build();
    }
}
