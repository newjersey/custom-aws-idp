package gov.nj.innovation.customAwsIdp.lambda.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import gov.nj.innovation.customAwsIdp.lambda.helpers.data.CognitoGroupDescriptionMetadata;
import org.apache.log4j.Logger;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetGroupResponse;

/**
 * Extract metadata stored as YAML in the description of a Cognito Group.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class CognitoGroupDescriptionMetadataExtractor {

    private static final Logger logger = Logger.getLogger(CognitoGroupDescriptionMetadataExtractor.class);
    final static ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    /**
     * Perform the YAML metadata extraction from a Cognito Group's description.
     *
     * @param region AWS Region of the Cognito UserPool
     * @param groupName Name of the Group whose description has the desired YAML metadata
     * @param userPoolId ID of the UserPool to which the Group belongs
     * @return {@link CognitoGroupDescriptionMetadata} representing the parsed YAML from the Group description.
     */
    public static CognitoGroupDescriptionMetadata extract(String region, String groupName, String userPoolId) {
        try (final CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .build()) {
            final GetGroupResponse getGroupResponse = cognitoClient.getGroup(GetGroupRequest.builder()
                    .userPoolId(userPoolId)
                    .groupName(groupName)
                    .build());
            final String groupDescription = getGroupResponse.group().description();
            final CognitoGroupDescriptionMetadata groupDescriptionMetadata =
                    MAPPER.readValue(groupDescription, CognitoGroupDescriptionMetadata.class);

            logger.info(String.format(
                    "Successfully fetched and parsed group description for userPoolId %s and groupName %s",
                    userPoolId, groupName));
            return groupDescriptionMetadata;
        } catch (RuntimeException | JsonProcessingException e) {
            final String errorMessage = String.format(
                    "Exception while trying to connect to/query Cognito for a group description, " +
                            "or while parsing YAML: %s (input [region: %s, groupName: %s, userPoolId: %s])",
                    e.getMessage(), region, groupName, userPoolId);
            throw new RuntimeException(errorMessage, e);
        }
    }
}
