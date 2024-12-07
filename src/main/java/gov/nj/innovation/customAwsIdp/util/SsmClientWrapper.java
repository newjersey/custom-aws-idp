package gov.nj.innovation.customAwsIdp.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * Fetch an SSM parameter by name.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public class SsmClientWrapper {

    private static final Logger logger = LogManager.getLogger(SsmClientWrapper.class);

    /**
     * Encapsulate the Amazon types; consume a String and output its SSM value.
     *
     * @param parameterName An SSM parameter name
     * @return The SSM parameter value.
     */
    public static String getParameterByName(final SsmClient ssmClient, final String parameterName) {
        logger.info("cw - ssmClientWrapper getting parameter {}", parameterName);
        final GetParameterRequest request = GetParameterRequest.builder()
                .name(parameterName)
                .withDecryption(true)
                .build();
        logger.info("cw - ssmClientWrapper running api");
        final GetParameterResponse response = ssmClient.getParameter(request);
        logger.info("cw - ssmClientWrapper got response {}", response);
        return response.parameter().value();
    }
}
