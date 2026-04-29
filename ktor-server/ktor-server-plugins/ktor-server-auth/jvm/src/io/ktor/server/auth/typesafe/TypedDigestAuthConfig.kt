/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

/**
 * Configures a typed Digest authentication scheme.
 *
 * Unlike [DigestAuthenticationProvider.Config], [validate] returns [P] so routes protected by [authenticateWith] can
 * read [principal] as the configured type.
 *
 * This config does not expose provider-level `challenge`. Set [onUnauthorized] or pass `onUnauthorized` to
 * [authenticateWith] to customize failure responses.
 *
 * Challenge strategy: a route-level `onUnauthorized` is used first, then [onUnauthorized]. If neither is configured,
 * Digest authentication responds with one `WWW-Authenticate: Digest` challenge for each configured algorithm, including
 * [realm], a new nonce, supported qop values, UTF-8 charset when configured, and `userhash=true` when a
 * [userHashResolver] is configured.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedDigestAuthConfig)
 *
 * @param P the principal type produced by this scheme.
 */
@ExperimentalKtorApi
@KtorDsl
public class TypedDigestAuthConfig<P : Any> @PublishedApi internal constructor() {
    /**
     * Human-readable description of this authentication scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedDigestAuthConfig.description)
     */
    public var description: String? = null

    /**
     * Realm passed in the `WWW-Authenticate` header.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedDigestAuthConfig.realm)
     */
    public var realm: String = "Ktor Server"

    /**
     * Message digest algorithms advertised by the server.
     *
     * When multiple algorithms are configured, the server sends multiple `WWW-Authenticate` headers and lets the
     * client choose one.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedDigestAuthConfig.algorithms)
     */
    public var algorithms: List<DigestAlgorithm> =
        listOf(DigestAlgorithm.SHA_512_256, @Suppress("DEPRECATION") DigestAlgorithm.MD5)

    /**
     * Supported Quality of Protection options.
     *
     * Include [DigestQop.AUTH_INT] only when the request body can be read during authentication.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedDigestAuthConfig.supportedQop)
     */
    public var supportedQop: List<DigestQop> = listOf(DigestQop.AUTH)

    /**
     * Charset used by Digest authentication.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedDigestAuthConfig.charset)
     */
    public var charset: Charset = Charsets.UTF_8

    /**
     * [NonceManager] used to generate and verify nonce values.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedDigestAuthConfig.nonceManager)
     */
    public var nonceManager: NonceManager = GenerateOnlyNonceManager

    /**
     * Default handler for authentication failures.
     *
     * A route-level `onUnauthorized` passed to [authenticateWith] overrides this handler. If both are `null`, Digest
     * authentication sends the default challenge described by this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedDigestAuthConfig.onUnauthorized)
     */
    public var onUnauthorized: (suspend (ApplicationCall, AuthenticationFailedCause) -> Unit)? = null

    private var validateFn: (suspend ApplicationCall.(DigestCredential) -> P?)? = null
    private var digestProviderFn: DigestProviderFunctionV2? = null
    private var legacyDigestProviderFn: DigestProviderFunction? = null
    private var userHashResolverFn: UserHashResolverFunction? = null
    private var strictMode: Boolean = false

    /**
     * Sets a validation function for [DigestCredential].
     *
     * Return a principal of type [P] when authentication succeeds, or `null` when the verified credentials should not
     * be accepted.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedDigestAuthConfig.validate)
     *
     * @param body validation function called after Digest credentials are verified.
     */
    public fun validate(body: suspend ApplicationCall.(DigestCredential) -> P?) {
        validateFn = body
    }

    /**
     * Configures the digest provider used to look up `H(username:realm:password)`.
     *
     * This overload does not receive the selected [DigestAlgorithm]. Use the overload that accepts
     * [DigestProviderFunctionV2] for full RFC 7616 support.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedDigestAuthConfig.digestProvider)
     *
     * @param digest provides a digest for a username and realm, or `null` when the user is unknown.
     */
    public fun digestProvider(digest: DigestProviderFunction) {
        legacyDigestProviderFn = digest
    }

    /**
     * Configures the digest provider used to look up `H(username:realm:password)` for the selected algorithm.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedDigestAuthConfig.digestProvider)
     *
     * @param digest provides a digest for a username, realm, and algorithm, or `null` when the user is unknown.
     */
    public fun digestProvider(digest: DigestProviderFunctionV2) {
        digestProviderFn = digest
    }

    /**
     * Configures a resolver for Digest `userhash` support.
     *
     * When configured, the server can accept hashed usernames and advertise `userhash=true` in challenges.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedDigestAuthConfig.userHashResolver)
     *
     * @param resolver resolves a user hash to the original username.
     */
    public fun userHashResolver(resolver: UserHashResolverFunction) {
        userHashResolverFn = resolver
    }

    /**
     * Enables strict RFC 7616 mode for Digest authentication.
     *
     * Strict mode removes deprecated MD5 algorithms and uses UTF-8.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedDigestAuthConfig.strictRfc7616Mode)
     */
    public fun strictRfc7616Mode() {
        strictMode = true
    }

    @PublishedApi
    internal fun buildProvider(name: String): DigestAuthenticationProvider {
        val config = DigestAuthenticationProvider.Config(name, description)
        config.realm = realm
        config.algorithms = algorithms
        config.supportedQop = supportedQop
        config.charset = charset
        config.nonceManager = nonceManager
        validateFn?.let { fn -> config.validate { credential -> fn(credential) } }
        digestProviderFn?.let { config.digestProvider(it) }
        legacyDigestProviderFn?.let { config.digestProvider(it) }
        userHashResolverFn?.let { config.userHashResolver(it) }
        if (strictMode) config.strictRfc7616Mode()
        return DigestAuthenticationProvider(config)
    }
}
