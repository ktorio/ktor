## 1. Goal
Fix the still-valid protected-resource metadata issues in `ktor-server-auth-oidc`: stale provider snapshots, non-bearer provider inclusion in derived metadata, and invalid IPv6 discovery URLs.

## 2. Approach
Use copy-on-write provider registry updates in `Oidc` so readers see an immutable snapshot and no request path iterates a mutating `HashMap`. Remove the route-level lazy metadata cache in `OidcProtectedResource` so each metadata response is rebuilt from the latest snapshot, then filter derived authorization servers and scopes to providers that actually have `bearerConfig`. Preserve explicit metadata overrides exactly as today.

## 3. File Changes
- **Modify** `ktor-server/ktor-server-plugins/ktor-server-auth-oidc/jvm/src/io/ktor/server/auth/oidc/Oidc.kt:200-205`
  - Change `providers` from a mutable `HashMap<String, OidcProvider<*>>` to a volatile read-only `Map<String, OidcProvider<*>>`, initialized with `emptyMap()` or `mapOf()`.
- **Modify** `ktor-server/ktor-server-plugins/ktor-server-auth-oidc/jvm/src/io/ktor/server/auth/oidc/Oidc.kt:271-283`
  - Keep duplicate-name and duplicate-issuer checks against the current `providers` snapshot; no behavior change except the snapshot is immutable.
- **Modify** `ktor-server/ktor-server-plugins/ktor-server-auth-oidc/jvm/src/io/ktor/server/auth/oidc/Oidc.kt:301-307`
  - In `configureProtectedResourceRoute()`, take a local snapshot (`val snapshot = providers`) before mapping `snapshot.values.map { it.config }`, so the route passes a stable provider-config list to metadata construction.
- **Modify** `ktor-server/ktor-server-plugins/ktor-server-auth-oidc/jvm/src/io/ktor/server/auth/oidc/Oidc.kt:317-328`
  - In `commitProvider()`, replace in-place `providers[provider.name] = provider` with copy-on-write assignment (`providers = providers + (provider.name to provider)`) after route setup and metadata refresh setup.
- **Modify** `ktor-server/ktor-server-plugins/ktor-server-auth-oidc/jvm/src/io/ktor/server/auth/oidc/OidcProtectedResource.kt:15-31`
  - Remove `val metadata by lazy { ... }` and build metadata inside the `get` handler with `buildProtectedResourceMetadata(config, providers())`, ensuring late provider registrations are reflected on subsequent requests.
- **Modify** `ktor-server/ktor-server-plugins/ktor-server-auth-oidc/jvm/src/io/ktor/server/auth/oidc/OidcProtectedResource.kt:34-54`
  - Add `val bearerProviders = providers.filter { it.bearerConfig != null }`.
  - Derive default `authorizationServers` from `bearerProviders.map { it.issuer }.distinct().ifEmpty { null }`.
  - Derive default `scopesSupported` from `bearerProviders.mapNotNull { it.oauthConfig?.scopes }.flatten().distinct().ifEmpty { null }`.
  - Keep `bearerMethodsSupported` behavior based on default-header bearer providers (`tokenExtractor == null`) and keep explicit config overrides taking precedence.
- **Modify** `ktor-server/ktor-server-plugins/ktor-server-auth-oidc/jvm/src/io/ktor/server/auth/oidc/OidcProtectedResource.kt:74-79`
  - Rebuild the resource metadata URL authority from parsed URI data while preserving IPv6 brackets. Use `uri.rawAuthority` or a helper that strips any userinfo (already rejected by `parseProtectedResourceUri`) and drops the parsed port suffix before re-appending `uri.port`, so `https://[::1]:8443/v1` becomes `https://[::1]:8443/.well-known/oauth-protected-resource/v1`.
- **Modify** `ktor-server/ktor-server-plugins/ktor-server-auth-oidc/jvm/test/io/ktor/server/auth/oidc/ProtectedResourceMetadataTest.kt:20-350`
  - Add regression tests in the existing test class for late provider registration after the first metadata request, bearer-only issuer/scope derivation, and IPv6 metadata URL generation.

## 4. Implementation Steps

### Task 1: Make provider registry reads stable
1. In `Oidc.kt:200-201`, change the registry declaration to a volatile read-only map, for example `private var providers: Map<String, OidcProvider<*>> = emptyMap()`.
2. In `Oidc.kt:271-283`, leave validation logic intact but ensure it reads from the immutable map snapshot.
3. In `Oidc.kt:317-328`, replace the in-place mutation with copy-on-write assignment after `startRefreshingMetadata(provider)`: assign `providers = providers + (provider.name to provider)`.
4. In `Oidc.kt:301-307`, update the provider lambda to take a local snapshot before mapping provider configs.

### Task 2: Rebuild protected-resource metadata from the latest snapshot
1. In `OidcProtectedResource.kt:15-31`, remove the route-level `lazy` value.
2. In the `get` handler, call `call.respond(buildProtectedResourceMetadata(config, providers()))` so provider additions after route installation and after a first metadata request are reflected.
3. Keep the content negotiation setup and route path generation unchanged.

### Task 3: Filter auto-derived metadata to bearer providers
1. In `OidcProtectedResource.kt:34-54`, define `bearerProviders` before deriving fields.
2. Change default `authorizationServers` and `scopesSupported` to use `bearerProviders` instead of all `providers`.
3. Keep `config.authorizationServers`, `config.scopesSupported`, and `config.bearerMethodsSupported` overrides unchanged so explicit values still win.
4. Keep `distinct()` and `ifEmpty { null }` behavior unchanged, now applied to the bearer-only collection.

### Task 4: Preserve IPv6 brackets in metadata URLs
1. In `OidcProtectedResource.kt:74-79`, add a small internal helper or local logic to derive the host authority from the parsed `URI` while preserving bracketed IPv6 literals.
2. Continue validating with `parseProtectedResourceUri(resource)` before building the URL.
3. Re-append `:${uri.port}` only when `uri.port != -1`, then append `/.well-known/oauth-protected-resource` plus the trimmed resource path.

### Task 5: Add focused regression tests
1. In `ProtectedResourceMetadataTest.kt:20-350`, add a test that enables `protectedResource`, performs a first metadata request before registering a provider, registers a bearer provider, then verifies a second metadata request includes that provider's issuer and bearer methods.
2. Add a test with one OAuth/session-only provider and one bearer provider, verifying default `authorizationServers` and `scopesSupported` include only the bearer provider's issuer/scopes while explicit overrides still use configured values.
3. Extend `protected resource metadata routes follow resource path and port` or add a separate test for `buildResourceMetadataUrl("https://[::1]:8443/v1")`, asserting the returned URL is `https://[::1]:8443/.well-known/oauth-protected-resource/v1`.

## 5. Acceptance Criteria
- A metadata request made before any providers are registered returns metadata without freezing that empty provider list; a later request after registering a bearer provider includes that provider's issuer and `bearerMethodsSupported == listOf("header")`.
- `Oidc.kt:317-328` no longer mutates the provider map in place; each committed provider publishes a new map instance.
- `configureProtectedResourceRoute()` in `Oidc.kt:301-307` maps provider configs from a local immutable snapshot, not a live mutable `HashMap` iterator.
- `buildProtectedResourceMetadata()` in `OidcProtectedResource.kt:34-54` derives default `authorizationServers` only from configs where `bearerConfig != null`.
- `buildProtectedResourceMetadata()` in `OidcProtectedResource.kt:34-54` derives default `scopesSupported` only from bearer-provider OAuth scopes and still returns `null` when the bearer-only scope collection is empty.
- Explicit `authorizationServers`, `scopesSupported`, and `bearerMethodsSupported` values in `ProtectedResourceMetadataConfig` continue to override derived values.
- `buildResourceMetadataUrl("https://[::1]:8443/v1")` returns `https://[::1]:8443/.well-known/oauth-protected-resource/v1`.
- Existing protected-resource paths for hostnames and ports continue to match current expectations in `ProtectedResourceMetadataTest.kt:181-209`.

## 6. Verification Steps
- Run the focused JVM test class:
  - `./gradlew :ktor-server-auth-oidc:jvmTest --tests "io.ktor.server.auth.oidc.ProtectedResourceMetadataTest"`
- Run module formatting and lint as required by the repo guidelines:
  - `./gradlew :ktor-server-auth-oidc:formatKotlin`
  - `./gradlew :ktor-server-auth-oidc:lintKotlin`
- Run module assemble:
  - `./gradlew :ktor-server-auth-oidc:assemble`
- Run the module JVM test suite:
  - `./gradlew :ktor-server-auth-oidc:jvmTest`
- ABI validation/update is not expected because the planned changes are internal-only. If the implementation accidentally changes public/protected API, run:
  - `./gradlew :ktor-server-auth-oidc:updateKotlinAbi`
  - `./gradlew :ktor-server-auth-oidc:checkKotlinAbi`

## 7. Risks & Mitigations
- **Risk:** Rebuilding metadata on every metadata endpoint request removes caching and may add small per-request allocation.
  - **Mitigation:** The work is limited to mapping in-memory provider configs and protects correctness for late provider registration. If performance becomes a concern, add an internal versioned cache keyed by the provider map reference, but avoid that extra complexity for this fix.
- **Risk:** Using `rawAuthority` for IPv6 could accidentally preserve userinfo.
  - **Mitigation:** Keep `parseProtectedResourceUri()` validation first; it already rejects `rawUserInfo != null` in `OidcProtectedResource.kt:91`.
- **Risk:** Bearer-only scope derivation may change behavior for deployments that expected browser-only OAuth scopes in protected-resource metadata.
  - **Mitigation:** This is the requested correction. Existing explicit `scopesSupported` override remains available and must be covered by tests.