package gov.nj.innovation.customAwsIdp.lambda;

import gov.nj.innovation.customAwsIdp.lambda.helpers.CognitoGroupDescriptionMetadataExtractor;
import gov.nj.innovation.customAwsIdp.lambda.helpers.data.CognitoGroupDescriptionMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * Tests for {@link GetSamlResponseHandler}. Because of laziness (but also thoroughness), this will primarily be an
 * integration test suite.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class GetSamlResponseHandlerTest {

    private static final String EMAIL = "test@test.com";
    private static final MockedStatic<CognitoGroupDescriptionMetadataExtractor> EXTRACTOR_MOCKER =
            mockStatic(CognitoGroupDescriptionMetadataExtractor.class);

    /**
     * NOTE: This only works when running the tests locally. When running the tests with Gradle, the test-task uses its
     * own environment variables. Changing this will have no effect on Gradle tests.
     */
    @SystemStub
    private final EnvironmentVariables envVariables = new EnvironmentVariables(
            "KEY_PRIVATE_EXPONENT_NAME", "custom-aws-idp-private-key-private-exponent",
            "KEY_PRIME_P_NAME", "custom-aws-idp-private-key-prime-p",
            "KEY_PRIME_Q_NAME", "custom-aws-idp-private-key-prime-q",
            "KEY_PRIME_EXPONENT_P_NAME", "custom-aws-idp-private-key-prime-exponent-p",
            "KEY_PRIME_EXPONENT_Q_NAME", "custom-aws-idp-private-key-prime-exponent-q",
            "KEY_CRT_COEFFICIENT_NAME", "custom-aws-idp-private-key-crt-coefficient",
            "COGNITO_REGION", "us-east-1",
            "COGNITO_USER_POOL", "myPoolOfCoolUsers",
            "DEFAULT_SESSION_DURATION", "900",
            "PATH_PARAMETER_GROUP_NAME", "groupName"
    );

    @AfterAll
    static void releaseStaticMock() {
        EXTRACTOR_MOCKER.closeOnDemand();
    }

    @Test
    @DisplayName("A well configured SamlGenerator results in a SUCCESS response with a SAMLResponse in the output")
    void testSuccess() {
        setupNiceGroupDescriptionExtractor();
        GetSamlResponseHandler getSamlResponseHandler = createNiceGetSamlResponseHandler();
        Map<String, String> response = getSamlResponseHandler.handleRequest(
                setupHandlerInput("resx-sandbox", "1234", EMAIL, "[resx-sandbox]"), null);

        Assertions.assertTrue(response != null && !response.isEmpty(),
                "Response should be non-null and non-empty");
        Assertions.assertEquals("SUCCESS", response.get("status"), "Status should be good");
        Assertions.assertFalse(response.get("samlResponse").isBlank(), "SAMLResponse should not be blank");
        Assertions.assertTrue(response.get("error").isBlank(), "error should be blank");
    }

    @Test
    @DisplayName("Still gets a SUCCESS response with a SAMLResponse in the output when 'duration' uses the default")
    void testSuccessWithDefaultDuration() {
        setupNiceGroupDescriptionExtractor();
        GetSamlResponseHandler getSamlResponseHandler = createNiceGetSamlResponseHandler();
        Map<String, String> response = getSamlResponseHandler.handleRequest(
                setupHandlerInput("resx-sandbox", null, EMAIL, "[resx-sandbox]"), null);

        Assertions.assertTrue(response != null && !response.isEmpty(),
                "Response should be non-null and non-empty");
        Assertions.assertEquals("SUCCESS", response.get("status"), "Status should be good");
        Assertions.assertFalse(response.get("samlResponse").isBlank(), "SAMLResponse should not be blank");
        Assertions.assertTrue(response.get("error").isBlank(), "error should be blank");
    }

    private static Stream<Arguments> testBadGroupName() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(""));
    }
    @ParameterizedTest
    @MethodSource
    @DisplayName("Returns INPUT_ERROR with no SAMLResponse and an Error if groupName is null or blank")
    void testBadGroupName(String groupName) {
        GetSamlResponseHandler getSamlResponseHandler = createNiceGetSamlResponseHandler();
        Map<String, String> response = getSamlResponseHandler.handleRequest(
                setupHandlerInput(groupName, null, EMAIL, "[resx-sandbox]"), null);

        Assertions.assertTrue(response != null && !response.isEmpty(),
                "Response should be non-null and non-empty");
        Assertions.assertEquals("INPUT_ERROR", response.get("status"), "Status should be input error");
        Assertions.assertTrue(response.get("samlResponse").isBlank(),
                "samlResponse should be blank for bad user/ssoRole");
        Assertions.assertFalse(response.get("error").isBlank(), "error should not be null or blank");
    }

    @ParameterizedTest
    @ValueSource(strings = { "-1", "1", "", "apple", "899", "43201" })
    @DisplayName("Returns INPUT_ERROR with no SAMLResponse and an Error if duration is invalid")
    void testBadDuration(String duration) {
        GetSamlResponseHandler getSamlResponseHandler = createNiceGetSamlResponseHandler();
        Map<String, String> response = getSamlResponseHandler.handleRequest(
                setupHandlerInput("resx-sandbox", duration, EMAIL, "[resx-sandbox]"), null);

        Assertions.assertTrue(response != null && !response.isEmpty(),
                "Response should be non-null and non-empty");
        Assertions.assertEquals("INPUT_ERROR", response.get("status"), "Status should be input error");
        Assertions.assertTrue(response.get("samlResponse").isBlank(),
                "samlResponse should be blank for bad user/ssoRole");
        Assertions.assertFalse(response.get("error").isBlank(), "error should not be null or blank");
    }

    private static Stream<Arguments> testBadEmail() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(""));
    }
    @ParameterizedTest
    @MethodSource
    @DisplayName("Returns INPUT_ERROR with no SAMLResponse and an Error if Email is null or blank")
    void testBadEmail(String email) {
        GetSamlResponseHandler getSamlResponseHandler = createNiceGetSamlResponseHandler();
        Map<String, String> response = getSamlResponseHandler.handleRequest(
                setupHandlerInput("resx-sandbox", null, email, "[resx-sandbox]"), null);

        Assertions.assertTrue(response != null && !response.isEmpty(),
                "Response should be non-null and non-empty");
        Assertions.assertEquals("INPUT_ERROR", response.get("status"), "Status should be input error");
        Assertions.assertTrue(response.get("samlResponse").isBlank(),
                "samlResponse should be blank for bad user/ssoRole");
        Assertions.assertFalse(response.get("error").isBlank(), "error should not be null or blank");
    }

    @Test
    @DisplayName("Returns INPUT_ERROR with no SAMLResponse and an Error if groupName is not in the user's groups")
    void testNonConfiguredGroup() {
        GetSamlResponseHandler getSamlResponseHandler = createNiceGetSamlResponseHandler();
        Map<String, String> response = getSamlResponseHandler.handleRequest(
                setupHandlerInput("resx-sandbox", null, EMAIL, "[group1 group2]"), null);

        Assertions.assertTrue(response != null && !response.isEmpty(),
                "Response should be non-null and non-empty");
        Assertions.assertEquals("INPUT_ERROR", response.get("status"), "Status should be input error");
        Assertions.assertTrue(response.get("samlResponse").isBlank(),
                "samlResponse should be blank for bad user/ssoRole");
        Assertions.assertFalse(response.get("error").isBlank(), "error should not be null or blank");
    }

    @Test
    @DisplayName("Returns SYSTEM_ERROR with no SAMLResponse and an Error if the extractor throws an exception")
    void testYamlParserThrows() {
        EXTRACTOR_MOCKER
                .when(() -> CognitoGroupDescriptionMetadataExtractor.extract(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Outlook not so good"));
        GetSamlResponseHandler getSamlResponseHandler = createNiceGetSamlResponseHandler();

        Map<String, String> response = getSamlResponseHandler.handleRequest(
                setupHandlerInput("resx-sandbox", null, EMAIL, "[resx-sandbox]"), null);

        Assertions.assertTrue(response != null && !response.isEmpty(),
                "Response should be non-null and non-empty");
        Assertions.assertEquals("SYSTEM_ERROR", response.get("status"), "Status should be system error");
        Assertions.assertTrue(response.get("samlResponse").isBlank(),
                "samlResponse should be blank for bad user/ssoRole");
        Assertions.assertFalse(response.get("error").isBlank(), "error should not be null or blank");
    }

    /**
     * Create the input map following the layout described in the {@link GetSamlResponseHandler} class.
     */
    private Map<String, Object> setupHandlerInput(String groupName, String duration, String email, String groups) {
        Map<String, Object> input = new HashMap<>();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("groupName", groupName);
        input.put("pathParameters", pathParams);
        if (duration != null) {
            input.put("queryStringParameters", Map.of("duration", duration));
        }

        Map<String, String> claims = new HashMap<>();
        if (email != null) {
            claims.put("email", email);
        }
        if (groups != null) {
            claims.put("cognito:groups", groups);
        }
        input.put("requestContext", Map.of("authorizer", Map.of("jwt", Map.of("claims", claims))));

        return input;
    }

    private void setupNiceGroupDescriptionExtractor() {
        EXTRACTOR_MOCKER
                .when(() -> CognitoGroupDescriptionMetadataExtractor.extract(anyString(), anyString(), anyString()))
                .thenReturn(new CognitoGroupDescriptionMetadata("https://test.com", "specialrole"));
    }

    private static class MockSsmClient implements SsmClient {
        @Override
        public String serviceName() { return "MockService"; }
        @Override
        public void close() {}
        @Override
        public GetParameterResponse getParameter(GetParameterRequest getParameterRequest) {
            return GetParameterResponse.builder()
                    .parameter(Parameter.builder().name(getParameterRequest.name()).value("123").build())
                    .build();
        }
    }

    private GetSamlResponseHandler createNiceGetSamlResponseHandler() {
        GetSamlResponseHandler handler =  new GetSamlResponseHandler();
        handler.setSsmClient(new MockSsmClient());
        return handler;
    }
}
