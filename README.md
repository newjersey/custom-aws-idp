# custom-aws-idp
Dynamically generate SAMLResponse XML documents from a custom Identity Provider
without running a server. The primary goal is to create SAMLResponses which can
be used for logging in to AWS Connect.

## Background

This code was adopted from the [Keycloak](https://www.keycloak.org/) project
([source code](https://github.com/keycloak/keycloak)) in order to host a custom
SAML-based Identity Provider (IdP) without having to run a server or rely on
some external service. Other tools were investigated in JavaScript 
([@node-saml/node-saml](https://github.com/node-saml/node-saml/),
[xml-crypto](https://github.com/node-saml/xml-crypto/),
[saml-idp](https://github.com/mcguinness/saml-idp), and
[passport-saml](http://www.passportjs.org/packages/passport-saml/)), but they
all seemed to be bad fits (node-saml explicitly is intended to be used on the
Service Provider (SP) side, I was unable to generate a valid signature with
xml-crypto, saml-idp had insecure dependencies, and passport-saml depends on
[ExpressJS](https://expressjs.com/) which we aren't using).

In light of these issues, a dependable, open-source IdP was chosen, even though
our office generally does not support Java. A thin slice of <500 lines of code
has been copied, massaged, and adapted to generate those SAMLResponse documents
for logging in to AWS Connect.

## Requirements

1. This is built in Java 17 using Gradle

2. AWS (in the account where the target AWS Connect instance lives):
   1. Create a SAML-based Identity Provider in IAM using the metadata document
      [`src/main/resources/identity-provider-metadata.xml`](src/main/resources/identity-provider-metadata.xml)
   2. Create a Policy in IAM which allows getting federation tokens for the
      Connect instance. Generic example:
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
   4. Upload key-secrets to AWS Parameter Store:
      1. See the `KEY_...` environment variables defined in
         [serverless.yml](serverless.yml) and populate their corresponding SSM
         names in the Parameter Store; values are stored in our Bitwarden vault

3. Build and deploy the Lambda (with AWS credentials set):
   ```commandline
   ./gradlew clean build && serverless deploy
   ```

## What To Do in the Future

### Key Rotation
There are probably smart ways to generate keys / the constituent parts thereof
(`openssl` likely a good choice), but after all the issues faced with JavaScript
libraries using handmade keys, I didn't trust making the keys myself and instead
opted to copy keys generated inside a full running Keycloak. This was done by
running Keycloak in debug-mode from
[IDELauncher.java](https://github.com/keycloak/keycloak/blob/074e85b4b6b200d0554c07aba8ea1221ba79aab6/quarkus/server/src/main/java/org/keycloak/quarkus/_private/IDELauncher.java),
putting a break point after `keyPair` is instantiated in
[SamlProtocol.java](https://github.com/keycloak/keycloak/blob/074e85b4b6b200d0554c07aba8ea1221ba79aab6/services/src/main/java/org/keycloak/protocol/saml/SamlProtocol.java#L480),
and then printing all the internal data from both of the keys and the
certificate. I believe the keys/certificate defined with the details living in
Bitwarden should be valid until September 29, 2033, then new keys will be
needed.

### Updating the Keycloak Code

This repo uses Keycloak as a direct dependency so that Keycloak still holds
all extra logic and code needed for managing a SAML IdP, also managing any
transitive dependencies this code may rely on. As such, the Keycloak
dependencies should be kept up-to-date if there are security vulnerabilities,
but there could come a time when a Keycloak update brings breaking changes for
this repo.

The code here was created primarily by debugging and stepping through the
[_authenticated_ method from SamlProtocol.java](https://github.com/keycloak/keycloak/blob/074e85b4b6b200d0554c07aba8ea1221ba79aab6/services/src/main/java/org/keycloak/protocol/saml/SamlProtocol.java#L423),
in the face of breaking changes, this same effort may have to be redone. The
code was also intentionally left very similar to how it appeared in Keycloak at
version 22.0.3 in the hopes that it is easier to synchronize with any changes
from the source.
