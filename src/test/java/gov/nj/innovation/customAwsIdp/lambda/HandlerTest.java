package gov.nj.innovation.customAwsIdp.lambda;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
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

/**
 * Tests for {@link Handler}. Because of laziness (but also thoroughness), this will be an integration test suite.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class HandlerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String USER = "test@test.com";
    private static final String SSO_ROLE = "arn:aws:iam::274460373520:role/TempForTesting," +
            "arn:aws:iam::274460373520:saml-provider/TempForTesting";

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
            "DEFAULT_SESSION_DURATION", "900"
    );

    @Test
    @DisplayName("A well configured SamlGenerator results in a 200 response with a SAMLResponse in the output")
    void testSuccess() throws Exception {
        Handler handler = new Handler();
        String response = handler.handleRequest(
                Map.of(
                        "user", USER,
                        "ssoRole", SSO_ROLE,
                        "duration", "1234"
                ), null);
        Assertions.assertTrue(response != null && !response.isBlank(),
                "Response should be non-null and non-empty");
        // Convert from a String back to the Response object
        Response responseValue = OBJECT_MAPPER.readValue(response, Response.class);
        System.out.println(responseValue);
        Assertions.assertEquals(200, responseValue.statusCode(), "Status code should be 200");
        Assertions.assertNotNull(responseValue.samlResponse(),
                "SAMLResponse should be present and non-null in the response");
        Assertions.assertFalse(responseValue.samlResponse().isBlank(), "SAMLResponse should not be blank");
        Assertions.assertNull(responseValue.error(), "error should be null");
    }

    @Test
    @DisplayName("Still gets a 200 response with a SAMLResponse in the output when 'duration' uses the default")
    void testSuccessWithDefaultDuration() throws Exception {
        Handler handler = new Handler();
        String response = handler.handleRequest(
                Map.of(
                        "user", USER,
                        "ssoRole", SSO_ROLE
                ), null);
        Assertions.assertTrue(response != null && !response.isBlank(),
                "Response should be non-null and non-empty");

        Response responseValue = OBJECT_MAPPER.readValue(response, Response.class);
        System.out.println(responseValue);
        Assertions.assertEquals(200, responseValue.statusCode(), "Status code should be 200");
        Assertions.assertNotNull(responseValue.samlResponse(),
                "SAMLResponse should be present and non-null in the response");
        Assertions.assertFalse(responseValue.samlResponse().isBlank(), "SAMLResponse should not be blank");
        Assertions.assertNull(responseValue.error(), "error should be null");
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
    void testBadUserOrSsoRole(String user, String ssoRole) throws Exception {
        Handler handler = new Handler();
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("user", user);
        inputMap.put("ssoRole", ssoRole);
        inputMap.put("duration", "1234");

        String response = handler.handleRequest(inputMap, null);
        Assertions.assertTrue(response != null && !response.isBlank(),
                "Response should be non-null and non-empty");

        Response responseValue = OBJECT_MAPPER.readValue(response, Response.class);
        System.out.println(responseValue);
        Assertions.assertEquals(400, responseValue.statusCode(), "Status code should be 400");
        Assertions.assertNull(responseValue.samlResponse(), "SAMLResponse should be null for bad user/ssoRole");
        Assertions.assertTrue(responseValue.error() != null && !responseValue.error().isBlank(),
                "error should not be null or blank");
    }

    @ParameterizedTest
    @ValueSource(strings = { "-1", "1", "", "apple", "899", "43201" })
    @DisplayName("Returns 400 with no SAMLResponse and an Error if duration is invalid")
    void testBadUserOrSsoRole(String duration) throws Exception {
        Handler handler = new Handler();
        String response = handler.handleRequest(
                Map.of(
                        "user", USER,
                        "ssoRole", SSO_ROLE,
                        "duration", duration
                ), null);
        Assertions.assertTrue(response != null && !response.isBlank(),
                "Response should be non-null and non-empty");

        Response responseValue = OBJECT_MAPPER.readValue(response, Response.class);
        System.out.println(responseValue);
        Assertions.assertEquals(400, responseValue.statusCode(), "Status code should be 400");
        Assertions.assertNull(responseValue.samlResponse(), "SAMLResponse should be null for bad session");
        Assertions.assertTrue(responseValue.error() != null && !responseValue.error().isBlank(),
                "error should not be null or blank");
    }
}
