package gov.nj.innovation.customAwsIdp.lambda.helpers;

import gov.nj.innovation.customAwsIdp.lambda.helpers.data.CognitoGroupDescriptionMetadata;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClientBuilder;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetGroupResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CognitoGroupDescriptionMetadataExtractor}.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class CognitoGroupDescriptionMetadataExtractorTest {

    private static final MockedStatic<CognitoIdentityProviderClient> CLIENT_MOCKER = mockStatic(CognitoIdentityProviderClient.class);

    @Test
    @DisplayName("A simple and successful extraction")
    void testExtractorSuccess() {
        setupUglyMockingAndInjectGroupDescription("relayState: https://example.com\nssoRole: special-role-string");
        CognitoGroupDescriptionMetadata metadata =
                CognitoGroupDescriptionMetadataExtractor.extract("us-east-1", "group1", "test-user-pool");
        Assertions.assertEquals("https://example.com", metadata.relayState(), "relayState should match");
        Assertions.assertEquals("special-role-string", metadata.ssoRole(), "ssoRole should match");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "RelayState: https://example2.com\nssoRole: special-role-string2",
            "relayState: https://example2.com\nSsoRole: special-role-string2",
            "relayState: https://example2.com\nSSORole: special-role-string2"
    })
    @DisplayName("The YAML parser accepts multiple capitalization options")
    void testExtractorNameVariations(String yamlDescription) {
        setupUglyMockingAndInjectGroupDescription(yamlDescription);
        CognitoGroupDescriptionMetadata metadata =
                CognitoGroupDescriptionMetadataExtractor.extract("us-east-1", "group1", "test-user-pool");
        Assertions.assertEquals("https://example2.com", metadata.relayState(), "relayState should match");
        Assertions.assertEquals("special-role-string2", metadata.ssoRole(), "ssoRole should match");
    }

    private static Stream<Arguments> throwsOnBadInput() {
        return Stream.of(
                Arguments.of("relayState: https://example3.com\nssoRole: special-role-string3\nFoo: bar"),
                Arguments.of("relayState: https://example3.com"),
                Arguments.of("ssoRole: special-role-string3"),
                Arguments.of(""),
                Arguments.of("This isn't really a parse-able YAML string"),
                Arguments.of((Object) null)
        );
    }
    @ParameterizedTest
    @MethodSource
    @DisplayName("The YAML parser throws exceptions on: extra field, required field missing, empty input, weird input, null input")
    void throwsOnBadInput(String yamlDescription) {
        setupUglyMockingAndInjectGroupDescription(yamlDescription);
        Assertions.assertThrows(
                RuntimeException.class,
                () -> CognitoGroupDescriptionMetadataExtractor.extract("us-east-1", "group1", "test-user-pool"),
                String.format("YAML parser should throw on input: %s", yamlDescription));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void setupUglyMockingAndInjectGroupDescription(String description) {
        CognitoIdentityProviderClient client = mock(CognitoIdentityProviderClient.class);
        CognitoIdentityProviderClientBuilder builder = mock(CognitoIdentityProviderClientBuilder.class);
        GetGroupResponse groupResponse = mock(GetGroupResponse.class);
        GroupType group = mock(GroupType.class);
        CLIENT_MOCKER.when(CognitoIdentityProviderClient::builder).thenReturn(builder);
        when(builder.region(any())).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        when(client.getGroup(any(GetGroupRequest.class))).thenReturn(groupResponse);
        when(groupResponse.group()).thenReturn(group);

        when(group.description()).thenReturn(description);
    }
}
