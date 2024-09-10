package gov.nj.innovation.customAwsIdp.lambda;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import gov.nj.innovation.customAwsIdp.keys.KeyConstants;
import gov.nj.innovation.customAwsIdp.keys.KeysWrapper;
import gov.nj.innovation.customAwsIdp.SamlGenerator;
import gov.nj.innovation.customAwsIdp.lambda.helpers.CognitoGroupDescriptionMetadataExtractor;
import gov.nj.innovation.customAwsIdp.lambda.helpers.data.AuthorizerContextDetails;
import gov.nj.innovation.customAwsIdp.lambda.helpers.data.CognitoGroupDescriptionMetadata;
import gov.nj.innovation.customAwsIdp.lambda.helpers.data.RequestParameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.jetbrains.annotations.VisibleForTesting;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

import static com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent.ProxyRequestContext;

/**
 * Handler for getting a generated SAML Response, modified from code generated by Serverless ({@code
 * com.serverless.Handler}). This relies on the structure of the handler-input including:
 * <pre>
 *   {
 *       pathParameters: {
 *         groupName: GROUP_NAME
 *       },
 *       queryStringParameters: {
 *           duration: DURATION,
 *           ...
 *       },
 *       requestContext: {
 *           authorizer: {
 *               jwt: {
 *                   claims: {
 *                       email: EMAIL,
 *                       "cognito:groups": "[GROUP1 GROUP2 GROUP3 ...]",
 *                       ...
 *                   }
 *               }
 *           }
 *       },
 *       ...
 *   }
 * </pre>
 *
 * This shape seems to be most aligned with {@link APIGatewayProxyRequestEvent}, but unfortunately everything inside the
 * <code>authorizer</code> object is just a <code>Map</code> of <code>String</code> to <code>Object</code>, so obnoxious
 * casting must be done inside.
 * <p>
 * For security, the "user" passed to {@link SamlGenerator#SamlGenerator(String, String, String, KeysWrapper)} will be
 * sourced exclusively from the JWT claim "email", and the "groupName" passed in the pathParameters will be validated
 * against the "cognito:groups" claim. This code should not leak extra metadata about user configurations or other users
 * or configured groups when called, even in the face of errors. Only a group assigned to the JWT-validated Cognito User
 * shall be honored, resulting in a full status-200 response.
 * <p>
 * Note also that the value of "cognito:groups" is a String that is space-delimited and must be converted to a list.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class GetSamlResponseHandler implements RequestHandler<APIGatewayProxyRequestEvent, Map<String, String>> {

    private static final Logger logger = LogManager.getLogger(GetSamlResponseHandler.class);
    private static final String DEFAULT_DURATION = System.getenv("DEFAULT_SESSION_DURATION");
    private static final String REGION = System.getenv("COGNITO_REGION");
    private static final String USER_POOL = System.getenv("COGNITO_USER_POOL");
    private static final Pattern DIGITS_PATTERN = Pattern.compile("\\d+");
    private static final String PATH_PARAMETER_GROUP_NAME = System.getenv("PATH_PARAMETER_GROUP_NAME");
    private static final String DURATION_PARAMETER = "duration";
    private static final String AUTHORIZER_JWT = "jwt";
    private static final String JWT_CLAIMS = "claims";
    private static final String EMAIL_CLAIM = "email";
    private static final String COGNITO_GROUPS_CLAIM = "cognito:groups";

    // Left open (not final) on purpose for testing
    @VisibleForTesting
    private SsmClient ssmClient = SsmClient.builder().region(Region.of(REGION)).build();

    @Override
    public Map<String, String> handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        final RequestParameters rp = extractRequestParametersFromInput(input);
        if (rp.groupName() == null || rp.groupName().isBlank()) {
            return createErrorReturnMap(Status.INPUT_ERROR,
                    String.format("groupName must have a non-empty value, had %s", rp.groupName()));
        }
        if (rp.duration() == null || rp.duration().isBlank() || !DIGITS_PATTERN.matcher(rp.duration()).matches() ||
                Integer.parseInt(rp.duration()) < 900 || Integer.parseInt(rp.duration()) > 43200) {
            return createErrorReturnMap(Status.INPUT_ERROR,
                    String.format("Invalid duration, must be an int between 900 and 43200, was %s", rp.duration()));
        }

        final AuthorizerContextDetails acd = extractAuthorizerDetailsFromInput(input);
        if (acd.email() == null || acd.email().isBlank()) {
            return createErrorReturnMap(Status.INPUT_ERROR,
                    String.format("email for Cognito user session must have a non-empty value, had %s", acd.email()));
        }
        if (!acd.usersGroups().contains(rp.groupName())) {
            return createErrorReturnMap(Status.INPUT_ERROR,
                    String.format("User %s does not belong to a group named %s", acd.email(), rp.groupName()));
        }

        final CognitoGroupDescriptionMetadata ssoMetadata;
        try {
            ssoMetadata = CognitoGroupDescriptionMetadataExtractor.extract(REGION, rp.groupName(), USER_POOL);
        } catch (RuntimeException e) {
            return createErrorReturnMap(Status.SYSTEM_ERROR,
                    String.format("Error trying to extract metadata from Group %s in UserPool %s: %s",
                            rp.groupName(), USER_POOL, e.getMessage()));
        }

        final KeyConstants keyConstants;
        try {
            keyConstants = new KeyConstants(ssmClient);
        } catch (NullPointerException | NumberFormatException e) {
            return createErrorReturnMap(Status.SYSTEM_ERROR,
                    String.format("KeyConstants threw an exception: %s.\nNOTE: Please check that the " +
                            "key-secrets are correct in AWS Systems Manager", e.getMessage()));
        }

        try {
            final KeysWrapper keys = new KeysWrapper(keyConstants);
            final SamlGenerator generator = new SamlGenerator(acd.email(), ssoMetadata.ssoRole(), rp.duration(), keys);
            final String encodedSamlResponse = generator.getBase64SamlResponse();
            logger.info(String.format("Successfully generated SAML Response for user %s with role %s and duration %s",
                    acd.email(), ssoMetadata.ssoRole(), rp.duration()));
            return createReturnMap(Status.SUCCESS, encodedSamlResponse, ssoMetadata.relayState(), null);

        } catch (Exception e) {
            return createErrorReturnMap(Status.SYSTEM_ERROR,
                    String.format("Error in handler: user %s, ssoRole %s, duration %s; error message: %s",
                            acd.email(), ssoMetadata.ssoRole(), rp.duration(), e.getMessage()));
        }
    }

    private RequestParameters extractRequestParametersFromInput(final APIGatewayProxyRequestEvent input) {
        final Map<String, String> pathParams = input.getPathParameters();
        final String groupName = pathParams != null && !pathParams.isEmpty() ?
                pathParams.get(PATH_PARAMETER_GROUP_NAME) : null;

        final Map<String, String> queryStringParams = input.getQueryStringParameters();
        final String duration = queryStringParams != null  && !queryStringParams.isEmpty() ?
                queryStringParams.getOrDefault(DURATION_PARAMETER, DEFAULT_DURATION) : DEFAULT_DURATION;

        return new RequestParameters(groupName, duration);
    }

    /* Casting the Object results of authorizer.get() leads to unchecked warnings. Ignore them for now. */
    @SuppressWarnings("unchecked")
    private AuthorizerContextDetails extractAuthorizerDetailsFromInput(final APIGatewayProxyRequestEvent input) {
        final ProxyRequestContext requestContext = input.getRequestContext();
        final Map<String, Object> authorizer = requestContext != null ? requestContext.getAuthorizer() : null;
        final Map<String, Object> jwt = authorizer != null && !authorizer.isEmpty() ?
                (Map<String, Object>) authorizer.get(AUTHORIZER_JWT) : null;
        final Map<String, Object> claims = jwt != null && !jwt.isEmpty() ?
                (Map<String, Object>) jwt.get(JWT_CLAIMS) : null;

        final String email = claims != null && !claims.isEmpty() ? (String) claims.get(EMAIL_CLAIM) : null;
        final String usersGroupsString = claims != null && !claims.isEmpty() ?
                (String) claims.get(COGNITO_GROUPS_CLAIM) : null;
        final List<String> usersGroups = usersGroupsString != null ?
                List.of(usersGroupsString.substring(1, usersGroupsString.length() - 1).split(" ")) : null;

        return new AuthorizerContextDetails(email, usersGroups);
    }

    private Map<String, String> createErrorReturnMap(final Status status, final String error) {
        logger.error(error);
        return createReturnMap(status, null, null, error);
    }

    private Map<String, String> createReturnMap(
            final Status status,
            final String samlResponse,
            final String relayState,
            final String error) {
        return Map.of(
                "status", status.name(),
                "samlResponse", samlResponse != null ? samlResponse : "",
                "relayState", relayState != null ? relayState : "",
                "error", error != null ? error : ""
        );
    }

    @VisibleForTesting
    void setSsmClient(SsmClient ssmClient) {
        this.ssmClient = ssmClient;
    }

    private enum Status { SUCCESS, INPUT_ERROR, SYSTEM_ERROR }
}
