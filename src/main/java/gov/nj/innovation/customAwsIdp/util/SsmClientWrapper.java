package gov.nj.innovation.customAwsIdp.util;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * Fetch an SSM parameter by name.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class SsmClientWrapper {

    /**
     * Encapsulate the Amazon types; consume a String and output its SSM value.
     *
     * @param parameterName An SSM parameter name
     * @return The SSM parameter value.
     */
    public static String getParameterByName(final SsmClient ssmClient, final String parameterName) {
        final GetParameterRequest request = GetParameterRequest.builder()
                .name(parameterName)
                .withDecryption(true)
                .build();
        final GetParameterResponse response = ssmClient.getParameter(request);

        return response.parameter().value();
    }
}
