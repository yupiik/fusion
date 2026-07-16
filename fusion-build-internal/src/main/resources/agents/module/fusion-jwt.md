# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

JWT creation (signer) and validation (validator) on top of JDK crypto, configured through
`@RootConfiguration` records - no third party JOSE dependency.

## Entry points

- `io.yupiik.fusion.jwt.JwtSignerFactory` + `JwtSignerConfiguration`: token creation.
- `io.yupiik.fusion.jwt.JwtValidatorFactory` + `JwtValidatorConfiguration`: token validation, returns `Jwt`.
- `io.yupiik.fusion.jwt.PublicKeyLoader` / `PrivateKeyLoader`: PEM key loading.
- `io.yupiik.fusion.jwt.bean`: default beans wiring the factories from the configuration.

## Module rules

- Security sensitive: algorithm/claim validation changes need tests for the negative cases
  (expired, wrong issuer, wrong signature, `none` algorithm rejection).

{{{footer}}}
