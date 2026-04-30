/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.jwt.typesafe

import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Verification
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.utils.io.*

/**
 * Configures a typed JWT authentication scheme.
 *
 * Unlike [JWTAuthenticationProvider.Config], [validate] returns [P] so routes protected by [io.ktor.server.auth.typesafe.authenticateWith] can read
 * [io.ktor.server.auth.typesafe.principal] as the configured type.
 *
 * This config does not expose provider-level `challenge`. Set [onUnauthorized] or pass `onUnauthorized` to
 * [io.ktor.server.auth.typesafe.authenticateWith] to customize failure responses.
 *
 * Challenge strategy: a route-level `onUnauthorized` is used first, then [onUnauthorized]. If neither is configured,
 * JWT authentication responds to missing or invalid credentials with a `WWW-Authenticate` challenge for the default
 * authentication scheme (`Bearer` unless changed by [authSchemes]) and [realm].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedJwtAuthConfig)
 *
 * @param P the principal type produced by this scheme.
 */
@ExperimentalKtorApi
@KtorDsl
public class TypedJwtAuthConfig<P : Any> @PublishedApi internal constructor() {
    /**
     * Human-readable description of this authentication scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedJwtAuthConfig.description)
     */
    public var description: String? = null

    /**
     * JWT realm passed in the `WWW-Authenticate` header.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedJwtAuthConfig.realm)
     */
    public var realm: String = "Ktor Server"

    /**
     * Default handler for authentication failures.
     *
     * A route-level `onUnauthorized` passed to [io.ktor.server.auth.typesafe.authenticateWith] overrides this handler. If both are `null`, JWT
     * authentication sends the default challenge described by this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedJwtAuthConfig.onUnauthorized)
     */
    public var onUnauthorized: (suspend (ApplicationCall, AuthenticationFailedCause) -> Unit)? = null

    private var validateFn: (suspend ApplicationCall.(JWTCredential) -> P?)? = null
    private var authHeaderFn: ((ApplicationCall) -> HttpAuthHeader?)? = null
    private var authSchemes: AuthSchemes? = null
    private var verifierConfig: (JWTAuthenticationProvider.Config.() -> Unit)? = null

    /**
     * Configures how to retrieve an HTTP authentication header.
     *
     * By default, JWT authentication parses the `Authorization` header.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedJwtAuthConfig.authHeader)
     *
     * @param block returns an authentication header for the call, or `null` when no header is available.
     */
    public fun authHeader(block: (ApplicationCall) -> HttpAuthHeader?) {
        authHeaderFn = block
    }

    /**
     * Configures accepted authentication schemes.
     *
     * By default, only the `Bearer` scheme is accepted.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedJwtAuthConfig.authSchemes)
     *
     * @param defaultScheme scheme used in the default challenge.
     * @param additionalSchemes additional schemes accepted when validating the request.
     */
    public fun authSchemes(defaultScheme: String = "Bearer", vararg additionalSchemes: String) {
        this.authSchemes = AuthSchemes(defaultScheme, additionalSchemes.toList())
    }

    /**
     * Sets the [JWTVerifier] used to verify token format and signature.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedJwtAuthConfig.verifier)
     *
     * @param verifier verifies token format and signature.
     */
    public fun verifier(verifier: JWTVerifier) {
        verifierConfig = { verifier(verifier) }
    }

    /**
     * Sets a suspend function that selects the [JWTVerifier] for a token.
     *
     * Return `null` when no verifier can be created for the provided header.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedJwtAuthConfig.verifier)
     *
     * @param verifier resolves a verifier for the authentication header.
     */
    public fun verifier(verifier: suspend (HttpAuthHeader) -> JWTVerifier?) {
        verifierConfig = { verifier(verifier) }
    }

    /**
     * Creates a [JWTVerifier] from [jwkProvider] and [issuer].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedJwtAuthConfig.verifier)
     *
     * @param jwkProvider provides JSON Web Keys.
     * @param issuer expected token issuer.
     * @param configure configures JWT verification.
     */
    public fun verifier(jwkProvider: JwkProvider, issuer: String, configure: JWTConfigureFunction = {}) {
        verifierConfig = { verifier(jwkProvider, issuer, configure) }
    }

    /**
     * Creates a [JWTVerifier] from [jwkProvider].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedJwtAuthConfig.verifier)
     *
     * @param jwkProvider provides JSON Web Keys.
     * @param configure configures JWT verification.
     */
    public fun verifier(jwkProvider: JwkProvider, configure: JWTConfigureFunction = {}) {
        verifierConfig = { verifier(jwkProvider, configure) }
    }

    /**
     * Creates a [JWTVerifier] for the given [issuer], [audience], and [algorithm].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedJwtAuthConfig.verifier)
     *
     * @param issuer expected token issuer.
     * @param audience expected token audience.
     * @param algorithm algorithm used to verify token signatures.
     * @param block customizes the underlying JWT verification.
     */
    public fun verifier(
        issuer: String,
        audience: String,
        algorithm: Algorithm,
        block: Verification.() -> Unit = {}
    ) {
        verifierConfig = { verifier(issuer, audience, algorithm, block) }
    }

    /**
     * Creates a [JWTVerifier] using JSON Web Keys discovered from [issuer].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedJwtAuthConfig.verifier)
     *
     * @param issuer expected token issuer and JWK provider base URL.
     * @param block configures JWT verification.
     */
    public fun verifier(issuer: String, block: JWTConfigureFunction = {}) {
        verifierConfig = { verifier(issuer, block) }
    }

    /**
     * Sets a validation function for [JWTCredential].
     *
     * Return a principal of type [P] when authentication succeeds, or `null` when the verified JWT should not be
     * accepted.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedJwtAuthConfig.validate)
     *
     * @param body validation function called after the token is verified.
     */
    public fun validate(body: suspend ApplicationCall.(JWTCredential) -> P?) {
        validateFn = body
    }

    @PublishedApi
    internal fun buildProvider(name: String): JWTAuthenticationProvider {
        val config = JWTAuthenticationProvider.Config(name, description)
        config.realm = realm
        authHeaderFn?.let { config.authHeader(it) }
        authSchemes?.let { schemes ->
            config.authSchemes(schemes.defaultScheme, *schemes.additionalSchemes.toTypedArray())
        }
        verifierConfig?.invoke(config)
        validateFn?.let { fn -> config.validate { credential -> fn(credential) } }
        return JWTAuthenticationProvider(config)
    }

    private class AuthSchemes(
        val defaultScheme: String,
        val additionalSchemes: List<String>
    )
}
