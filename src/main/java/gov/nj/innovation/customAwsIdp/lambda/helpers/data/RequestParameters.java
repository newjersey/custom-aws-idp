package gov.nj.innovation.customAwsIdp.lambda.helpers.data;

/**
 * Wrapper for the data extracted from the "pathParameters" and the "queryStringParameters" of the input for {@link
 * com.amazonaws.services.lambda.runtime.RequestHandler#handleRequest(Object,
 * com.amazonaws.services.lambda.runtime.Context)}.
 *
 * @param groupName The group (which the Cognito user belongs to) containing necessary SAML-generation metadata
 * @param duration The optional query parameter representing how long the SSO session should be valid
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public record RequestParameters(String groupName, String duration) {}
