package gov.nj.innovation.customAwsIdp.lambda;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Code inspired by the generated class {@code com.serverless.ApiGatewayResponse}.
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
public record Response(int statusCode, String samlResponse, String error) {

    private static final Logger logger = Logger.getLogger(Response.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String toString() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize object", e);
            throw new RuntimeException(e);
        }
    }
}