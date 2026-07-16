<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-jwt.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: JWT (`fusion-jwt`)

JWT signing and validation based on JDK cryptography.

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

## Configuration properties

Generated from the annotation processor metadata (`META-INF/fusion/configuration/documentation.json`):

| Name | Env variable | Description | Default | Required |
|---|---|---|---|---|
| `jwt-signer.algorithm` | `JWT_SIGNER_ALGORITHM` | Default JWT `alg` value if no `keys` is set (mainly useful for Hmac case). | `"RS256"` | no |
| `jwt-signer.expRequired` | `JWT_SIGNER_EXPREQUIRED` | Is `exp` (expiry) required. | `true` | no |
| `jwt-signer.expValidity` | `JWT_SIGNER_EXPVALIDITY` | Is `exp` is required the validity used in milliseconds. | `0` | no |
| `jwt-signer.iatRequired` | `JWT_SIGNER_IATREQUIRED` | Is `iat` (issued at) required. | `false` | no |
| `jwt-signer.issuer` | `JWT_SIGNER_ISSUER` | JWT issuer. | - | yes |
| `jwt-signer.key` | `JWT_SIGNER_KEY` | Private key. | - | yes |
| `jwt-signer.kid` | `JWT_SIGNER_KID` | KID header kid. | `"k001"` | no |
| `jwt-signer.nbfRequired` | `JWT_SIGNER_NBFREQUIRED` | Is `nbf` (not before) required. | `false` | no |
| `jwt.algo` | `JWT_ALGO` | Default JWT `alg` value if no `keys` is set (mainly useful for Hmac case). | `"RS256"` | no |
| `jwt.expRequired` | `JWT_EXPREQUIRED` | Is `exp` (expiry) validation required of can it be skipped if claim is missing. | `true` | no |
| `jwt.iatRequired` | `JWT_IATREQUIRED` | Is `iat` (issued at) validation required of can it be skipped if claim is missing. | `false` | no |
| `jwt.issuer` | `JWT_ISSUER` | JWT issuer, validation is ignored if null. | - | no |
| `jwt.jtiRequired` | `JWT_JTIREQUIRED` | Is `jti` (JWT ID) validation required of can it be skipped if claim is missing. | `true` | no |
| `jwt.key` | `JWT_KEY` | Default public key to use to validate the incoming JWT if no `keys` is set else `kid` is matched against the `keys` set (mainly useful for Hmac case which can't be in `jwk_uri`). | - | yes |
| `jwt.keys.$index.alg` | `JWT_KEYS_INDEX_ALG` |  | - | no |
| `jwt.keys.$index.crv` | `JWT_KEYS_INDEX_CRV` |  | - | no |
| `jwt.keys.$index.e` | `JWT_KEYS_INDEX_E` |  | - | no |
| `jwt.keys.$index.kid` | `JWT_KEYS_INDEX_KID` |  | - | no |
| `jwt.keys.$index.kty` | `JWT_KEYS_INDEX_KTY` |  | - | no |
| `jwt.keys.$index.n` | `JWT_KEYS_INDEX_N` |  | - | no |
| `jwt.keys.$index.use` | `JWT_KEYS_INDEX_USE` |  | - | no |
| `jwt.keys.$index.x` | `JWT_KEYS_INDEX_X` |  | - | no |
| `jwt.keys.$index.x5c` | `JWT_KEYS_INDEX_X5C` |  | - | no |
| `jwt.keys.$index.y` | `JWT_KEYS_INDEX_Y` |  | - | no |
| `jwt.nbfRequired` | `JWT_NBFREQUIRED` | Is `nbf` (not before) validation required of can it be skipped if claim is missing. | `false` | no |
| `jwt.tolerance` | `JWT_TOLERANCE` | Tolerance for date validation (in seconds). | `30` | no |

## Working in this module

- Build it: `mvn install -pl fusion-jwt -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.

