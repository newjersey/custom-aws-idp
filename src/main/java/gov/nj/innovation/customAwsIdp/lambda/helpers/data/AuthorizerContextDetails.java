package gov.nj.innovation.customAwsIdp.lambda.helpers.data;

import java.util.List;

/**
 * Wrapper for the data extracted from the "requestContext" of the input for {@link
 * com.amazonaws.services.lambda.runtime.RequestHandler#handleRequest(Object,
 * com.amazonaws.services.lambda.runtime.Context)}.
 *
 * @param email The email address of a Cognito user
 * @param usersGroups The groups which that user belongs to
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public record AuthorizerContextDetails(String email, List<String> usersGroups) {}
