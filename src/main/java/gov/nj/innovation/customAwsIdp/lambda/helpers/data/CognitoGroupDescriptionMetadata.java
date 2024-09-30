package gov.nj.innovation.customAwsIdp.lambda.helpers.data;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Class used for parsing the metadata in a Cognito Group's description.
 *
 * @param relayState The relay state used for a specific AWS Connect instance
 * @param ssoRole The value for the SAML <pre>https://aws.amazon.com/SAML/Attributes/Role</pre> parameter
 *
 * @author Case Walker (case@innovation.nj.gov)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CognitoGroupDescriptionMetadata(
        @JsonAlias({"relayState", "RelayState", "RELAYSTATE", "relaystate"})
        @JsonProperty(required = true)
        String relayState,
        @JsonAlias({"ssoRole", "SsoRole", "SSORole", "SSOROLE", "ssorole"})
        @JsonProperty(required = true)
        String ssoRole
) {}
