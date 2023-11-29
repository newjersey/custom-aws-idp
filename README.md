# Custom AWS Identity Provider (for AWS Connect)

Dynamically generate SAML Response XML documents from a custom Identity Provider
without running a server. The primary goal is to create SAML Responses which can
be used for logging in to AWS Connect.

## Background

This code was adopted from the [Keycloak](https://www.keycloak.org/) project
([source code](https://github.com/keycloak/keycloak)) in order to host a custom
SAML-based Identity Provider (IdP) without having to run a server or rely on
some external service. Other tools were investigated in JavaScript 
([@node-saml/node-saml](https://github.com/node-saml/node-saml/),
[xml-crypto](https://github.com/node-saml/xml-crypto/),
[saml-idp](https://github.com/mcguinness/saml-idp), and
[@node-saml/passport-saml](http://www.passportjs.org/packages/passport-saml/)),
but they all seemed to be bad fits (node-saml explicitly is intended to be used
on the Service Provider (SP) side, I was unable to generate a valid signature
with xml-crypto, saml-idp had insecure dependencies, and passport-saml may
actually work (I should definitely come back to
[this](https://github.com/node-saml/passport-saml/blob/master/docs/xml-signing-example.js)
especially), but I didn't look into it very hard after seeing that it was built
to be used with [ExpressJS](https://expressjs.com/), especially knowing that
this was only going to be backend code).

In light of these issues, a dependable, open-source IdP was chosen, even though
our office generally does not support Java. A 
[medium article](https://neuw.medium.com/aws-connect-saml-based-identity-provider-using-keycloak-9b3e6d0111e6)
about manually configuring exactly the type of Keycloak client needed here was
followed, and after that client was verified to correctly federate sign-ons to
AWS, an intensive live-debugging process allowed us to find a thin slice of ~500
lines of code along with hardcoded stand-ins that could be used in this
repository. The Keycloak code was copied, massaged, and adapted to be a
standalone solution for generating those SAML Response documents used for
logging in to AWS Connect.

## Requirements and Deployment

1. This is built in Java 17 using Gradle

2. AWS (in the account where the target AWS Connect instance lives):
   1. Create a SAML-based Identity Provider in IAM using the metadata document
      [`src/main/resources/identity-provider-metadata.xml`](src/main/resources/identity-provider-metadata.xml)

   2. Create a Policy in IAM which allows getting federation tokens for the
      target Connect instance. Generic example:
   ```json
   {
       "Version": "2012-10-17",
        "Statement": [{
            "Sid": "FederationForConnect",
            "Effect": "Allow",
            "Action": "connect:GetFederationToken",
            "Resource": "*",
            "Condition": {
                "StringEquals": {
                    "connect:InstanceId": "<TARGET_CONNECT_INSTANCE_ID>"
                }
            }
        }]
   }
   ```

   3. Create a Role:
      1. For _SAML 2.0 federation_
      2. Using the above-created Identity Provider
      3. With "_Allow programmatic and AWS Management Console access_"
      4. With the above-created Policy

   4. Upload key-secrets to AWS Systems Manager -> Parameter Store:
      1. See the `KEY_...` environment variables defined in
         [serverless.yml](serverless.yml) and populate their corresponding SSM
         names in the Parameter Store; values are stored in our Bitwarden vault

3. Build and deploy the Lambda (with AWS credentials set):
   ```commandline
   ./gradlew clean build && serverless deploy
   ```

## Usage

The Lambda handler is configured to expect and validate a Cognito user from the
"_us-east-1_AZyvZQdFN_" user pool. All ApiGateway requests must have a valid JWT
bearer token.

As well, this Cognito integration implicitly depends on Groups inside the user
pool. These groups should represent different AWS Connect instances, and they
**must** have the _RelayState_ (in the form of 
`https://us-east-1.console.aws.amazon.com/connect/federate/<CONNECT_INSTANCE_ID>`)
and _SsoRole_ (in the form of `<ARN_FOR_SAML2_ROLE>,<ARN_FOR_SAML_IDP>`) of the
target Connect instance defined as a YAML string inside the Group's description.
For example:
```yaml
RelayState: "https://us-east-1.console.aws.amazon.com/connect/federate/00000000-0000-0000-0000-000000000000"
SsoRole: "arn:aws:iam::000000000000:role/ROLE_NAME,arn:aws:iam::000000000000:saml-provider/IDP_NAME"
```

With all details setup, an Administrator would ensure the correct users are
assigned their correct groups, and a GET request is then made to (with the `duration` parameter left optional):
```
https://BASE_URL/generateSaml/<GROUP_NAME>?duration=<DURATION>
```

## What To Do in the Future

### Key Rotation

There are probably smart ways to generate keys/the constituent parts thereof
(`openssl` is likely a good choice), but after all the issues faced with
JavaScript libraries while using handmade keys, I didn't trust making the keys
myself and instead opted to copy working keys generated inside the full, running
Keycloak. This was done by running Keycloak in debug-mode from
[IDELauncher.java](https://github.com/keycloak/keycloak/blob/074e85b4b6b200d0554c07aba8ea1221ba79aab6/quarkus/server/src/main/java/org/keycloak/quarkus/_private/IDELauncher.java),
putting a break point after `keyPair` is instantiated in
[SamlProtocol.java](https://github.com/keycloak/keycloak/blob/074e85b4b6b200d0554c07aba8ea1221ba79aab6/services/src/main/java/org/keycloak/protocol/saml/SamlProtocol.java#L480),
and then printing all the internal data from both of the keys and the
certificate. I believe the keys/certificate defined with the details in this
repository and living in Bitwarden should be valid until September 29, 2033, at
which time if this solution is still in use, the keys will need to be replaced.

### Updating the Keycloak Code

This repo uses Keycloak as a direct dependency so that Keycloak still holds
all extra logic and code needed for managing a SAML IdP, also managing any
transitive dependencies this code may be relying on. As such, the Keycloak
dependencies should be kept up-to-date if there are any security
vulnerabilities. With that framing, there could come a time when a Keycloak
update brings breaking changes for the code in this repo.

The code here was created primarily by debugging and stepping through just one
method:
[_authenticated_ inside SamlProtocol.java](https://github.com/keycloak/keycloak/blob/074e85b4b6b200d0554c07aba8ea1221ba79aab6/services/src/main/java/org/keycloak/protocol/saml/SamlProtocol.java#L423).
In the face of breaking changes, this same effort may have to be redone. The
code was also intentionally left very similar to how it appeared in Keycloak at
version 22.0.3 in the hopes that it may be easier to synchronize with any
changes in the source.
