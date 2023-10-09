package gov.nj.innovation.customAwsIdp.lambda;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GetSamlResponseHandler}. Because of laziness (but also thoroughness), this will be an integration test suite.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class GetSamlResponseHandlerTest {

    private static final String USER = "test@test.com";
    private static final String SSO_ROLE = "arn:aws:iam::274460373520:role/TempForTesting," +
            "arn:aws:iam::274460373520:saml-provider/TempForTesting";
    private static final Context TEST_CONTEXT = mock(Context.class);
    private static final ClientContext CLIENT_CONTEXT = mock(ClientContext.class);
    private static final CognitoIdentity COGNITO_IDENTITY = mock(CognitoIdentity.class);

    /**
     * NOTE: This only works when running the tests locally. When running the tests with Gradle, the test-task uses its
     * own environment variables. Changing this will have no effect on Gradle tests.
     */
    @SystemStub
    private final EnvironmentVariables envVariables = new EnvironmentVariables(
            "KEY_PRIVATE_EXPONENT", "123",
            "KEY_PRIME_P", "123",
            "KEY_PRIME_Q", "123",
            "KEY_PRIME_EXPONENT_P", "123",
            "KEY_PRIME_EXPONENT_Q", "123",
            "KEY_CRT_COEFFICIENT", "123",
            "COGNITO_REGION", "us-east-1",
            "COGNITO_USER_POOL", "myPoolOfCoolUsers",
            "DEFAULT_SESSION_DURATION", "900"
    );

    @BeforeEach
    void setupMocks() {
        when(TEST_CONTEXT.getClientContext()).thenReturn(CLIENT_CONTEXT);
        when(TEST_CONTEXT.getIdentity()).thenReturn(COGNITO_IDENTITY);
    }

    @Test
    @DisplayName("A well configured SamlGenerator results in a 200 response with a SAMLResponse in the output")
    void testSuccess() {
        GetSamlResponseHandler getSamlResponseHandler = new GetSamlResponseHandler();
        Map<String, String> response = getSamlResponseHandler.handleRequest(
                Map.of("pathParameters", Map.of("user", USER, "ssoRole", SSO_ROLE),
                        "queryStringParameters", Map.of("duration", "1234")), TEST_CONTEXT);

        Assertions.assertTrue(response != null && !response.isEmpty(),
                "Response should be non-null and non-empty");
        Assertions.assertEquals("200", response.get("status"), "Status code should be 200");
        Assertions.assertFalse(response.get("samlResponse").isBlank(), "SAMLResponse should not be blank");
        Assertions.assertTrue(response.get("error").isBlank(), "error should be blank");
    }

    @Test
    @DisplayName("Still gets a 200 response with a SAMLResponse in the output when 'duration' uses the default")
    void testSuccessWithDefaultDuration() {
        GetSamlResponseHandler getSamlResponseHandler = new GetSamlResponseHandler();
        Map<String, String> response = getSamlResponseHandler.handleRequest(
                Map.of("pathParameters", Map.of("user", USER, "ssoRole", SSO_ROLE)), TEST_CONTEXT);

        Assertions.assertTrue(response != null && !response.isEmpty(),
                "Response should be non-null and non-empty");
        Assertions.assertEquals("200", response.get("status"), "Status code should be 200");
        Assertions.assertFalse(response.get("samlResponse").isBlank(), "SAMLResponse should not be blank");
        Assertions.assertTrue(response.get("error").isBlank(), "error should be blank");
    }

    private static Stream<Arguments> testBadUserOrSsoRole() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(USER, null),
                Arguments.of(null, SSO_ROLE),
                Arguments.of("", ""),
                Arguments.of(USER, ""),
                Arguments.of("", SSO_ROLE)
        );
    }
    @ParameterizedTest
    @MethodSource
    @DisplayName("Returns 400 with no SAMLResponse and an Error if user and/or ssoRole are null or blank")
    void testBadUserOrSsoRole(String user, String ssoRole) {
        GetSamlResponseHandler getSamlResponseHandler = new GetSamlResponseHandler();
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("user", user);
        inputMap.put("ssoRole", ssoRole);
        Map<String, String> response = getSamlResponseHandler.handleRequest(Map.of("pathParameters", inputMap), TEST_CONTEXT);

        Assertions.assertTrue(response != null && !response.isEmpty(),
                "Response should be non-null and non-empty");
        Assertions.assertEquals("400", response.get("status"), "Status code should be 400");
        Assertions.assertTrue(response.get("samlResponse").isBlank(),
                "samlResponse should be blank for bad user/ssoRole");
        Assertions.assertFalse(response.get("error").isBlank(), "error should not be null or blank");
    }

    @ParameterizedTest
    @ValueSource(strings = { "-1", "1", "", "apple", "899", "43201" })
    @DisplayName("Returns 400 with no SAMLResponse and an Error if duration is invalid")
    void testBadUserOrSsoRole(String duration) {
        GetSamlResponseHandler getSamlResponseHandler = new GetSamlResponseHandler();
        Map<String, String> response = getSamlResponseHandler.handleRequest(
                Map.of("pathParameters", Map.of("user", USER, "ssoRole", SSO_ROLE),
                        "queryStringParameters", Map.of("duration", duration)), TEST_CONTEXT);

        Assertions.assertTrue(response != null && !response.isEmpty(),
                "Response should be non-null and non-empty");
        Assertions.assertEquals("400", response.get("status"), "Status code should be 400");
        Assertions.assertTrue(response.get("samlResponse").isBlank(),
                "samlResponse should be blank for bad user/ssoRole");
        Assertions.assertFalse(response.get("error").isBlank(), "error should not be null or blank");
    }
}
