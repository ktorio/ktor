/*
* Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.*
import java.security.MessageDigest

/**
 * A `digest` [Authentication] provider.
 *
 * This provider supports:
 * - Multiple hash algorithms: MD5, SHA-256, SHA-512-256 (and their session variants)
 * - Quality of Protection (qop): auth and auth-int
 * - User hash for privacy protection
 * - UTF-8 charset support
 *
 * **Security Note**: SHA-512-256 is the recommended hash algorithm for new implementations.
 * While MD5 is supported for backward compatibility, it is deprecated and should be avoided
 * in production. Consider enabling [Config.strictRfc7616Mode] to enforce stronger algorithms.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestAuthenticationProvider)
 *
 * @property realm specifies the value to be passed in the `WWW-Authenticate` header.
 */
public class DigestAuthenticationProvider internal constructor(
    config: Config
) : AuthenticationProvider(config) {

    private val realm: String = config.realm

    private val algorithms: List<DigestAlgorithm> = config.algorithms

    private val defaultAlgorithm: DigestAlgorithm = algorithms.firstOrNull() ?: DigestAlgorithm.SHA_512_256

    private val qopValues = config.supportedQop.map { it.value }

    private val charset: Charset? = config.charset

    private val userHashResolver: UserHashResolverFunction? = config.userHashResolver

    private val nonceManager: NonceManager = config.nonceManager

    private val userNameRealmPasswordDigestProvider: DigestProviderFunctionV2 = config.digestProvider

    private val authenticationFunction: AuthenticationFunction<DigestCredential> = config.authenticationFunction

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val authorizationHeader = call.request.parseAuthorizationHeader()
        val credentials = authorizationHeader?.let { authHeader ->
            if (authHeader.authScheme == AuthScheme.Digest && authHeader is HttpAuthHeader.Parameterized) {
                authHeader.toDigestCredential(defaultCharset = charset ?: Charsets.ISO_8859_1)
            } else {
                null
            }
        }

        val algorithmName = (credentials?.algorithm ?: defaultAlgorithm.algorithmName)
        val algorithm = DigestAlgorithm.from(algorithmName)

        // Store HA1 for use in the Authentication-Info header
        var verifiedHa1: ByteArray? = null
        var verifiedBodyHash: ByteArray? = null

        val verify: suspend (DigestCredential, String) -> Boolean = { credential, actualUserName ->
            val userDigest = userNameRealmPasswordDigestProvider(actualUserName, credential.realm, algorithm!!)
            val ha1 = credential.computeHA1(userNameRealmPasswordDigest = userDigest ?: ByteArray(0))
            val entityBodyHash = when {
                credentials?.qop == DigestQop.AUTH_INT.value -> call.computeBodyHash(credential.digester)
                else -> null
            }
            verifiedHa1 = ha1
            verifiedBodyHash = entityBodyHash
            credential.verifyWithHA1(call.request.local.method, ha1, entityBodyHash) && userDigest != null
        }

        val principal = credentials?.let { credential ->
            // Resolve actual username when userhash is enabled
            val resolvedUserName: String? = when {
                algorithm == null -> null
                credential.userHash -> userHashResolver?.invoke(credentials.userName, credentials.realm, algorithm)
                else -> credentials.userName
            }

            if (resolvedUserName != null &&
                algorithms.any { it == algorithm } &&
                credential.realm == realm &&
                nonceManager.verifyNonce(credential.nonce) &&
                validateQop(credential.qop) &&
                verify(credential, resolvedUserName)
            ) {
                val credentialForAuth = when {
                    credential.userHash -> credential.copy(userName = resolvedUserName)
                    else -> credential
                }
                call.authenticationFunction(credentialForAuth)
            } else {
                null
            }
        }

        when (principal) {
            null -> {
                val cause = when (credentials) {
                    null -> AuthenticationFailedCause.NoCredentials
                    else -> AuthenticationFailedCause.InvalidCredentials
                }

                @Suppress("NAME_SHADOWING")
                context.challenge(digestAuthenticationChallengeKey, cause) { challenge, call ->
                    val supportsUserHash = userHashResolver != null
                    val challenges = algorithms.map { algorithm ->
                        HttpAuthHeader.digestAuthChallenge(
                            realm,
                            algorithm = algorithm.algorithmName,
                            nonce = nonceManager.newNonce(),
                            charset = charset,
                            userhash = supportsUserHash,
                            qop = qopValues
                        )
                    }
                    call.respond(UnauthorizedResponse(*challenges.toTypedArray()))
                    challenge.complete()
                }
            }

            else -> {
                // Add Authentication-Info header for successful authentication when qop is used
                if (credentials.qop != null) {
                    val authInfo = credentials.buildAuthenticationInfoHeader(
                        ha1 = verifiedHa1!!,
                        nextNonce = nonceManager.newNonce(),
                        responseBodyHash = verifiedBodyHash
                    )
                    call.response.header(HttpHeaders.AuthenticationInfo, authInfo)
                }
                context.principal(name, principal)
            }
        }
    }

    /**
     * Validates that this server supports the client's qop value.
     *
     * Per RFC 2617 backward compatibility, if the client doesn't send qop,
     * authentication can still proceed to use the legacy format.
     */
    private fun validateQop(clientQop: String?): Boolean {
        // If the client didn't send qop, allow it for RFC 2617 backward compatibility
        // If the client sent a qop, it must be the one the server supports
        return clientQop == null || qopValues.any { it == clientQop }
    }

    /**
     * Computes the hash of the request entity body for auth-int verification.
     */
    private suspend fun ApplicationCall.computeBodyHash(digester: MessageDigest): ByteArray {
        // Note: This consumes the body. Users should install the DoubleReceive plugin.
        val bodyBytes = runCatching { receive<ByteArray>() }.getOrNull()
            ?: ByteArray(0)
        digester.reset()
        return digester.digest(bodyBytes)
    }

    /**
     * A configuration for the [digest] authentication provider.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestAuthenticationProvider.Config)
     */
    public class Config internal constructor(
        name: String?,
        description: String?
    ) : AuthenticationProvider.Config(name, description) {
        internal var digestProvider: DigestProviderFunctionV2 = { userName, realm, algorithm ->
            val digester = algorithm.toDigester()
            digester.digest("$userName:$realm".toByteArray(Charsets.UTF_8))
        }

        internal var authenticationFunction: AuthenticationFunction<DigestCredential> = { UserIdPrincipal(it.userName) }

        /**
         * Specifies a realm to be passed in the `WWW-Authenticate` header.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestAuthenticationProvider.Config.realm)
         */
        public var realm: String = "Ktor Server"

        /**
         * A message digest algorithm to be used. Usually only `MD5` is supported by clients.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestAuthenticationProvider.Config.algorithmName)
         */
        @Deprecated("Use algorithms instead", ReplaceWith("algorithms"))
        public var algorithmName: String?
            get() = algorithms.firstOrNull()?.hashName
            set(value) {
                algorithms = when {
                    value == null -> listOf()
                    else -> DigestAlgorithm.from(value)?.let { listOf(it) }
                        ?: error("Unsupported digest algorithm: $value")
                }
            }

        /**
         * List of message digest algorithms to be used.
         *
         * Supported algorithms:
         * - `MD5` default, for backward compatibility (deprecated, avoid it in production)
         * - `MD5-sess` session variant, deprecated
         * - `SHA-256` recommended minimum for production use
         * - `SHA-256-sess` session variant
         * - `SHA-512-256` **recommended for new implementations** (provides the strongest security)
         * - `SHA-512-256-sess` session variant with the strongest security
         *
         * When multiple algorithms are configured, the server will send multiple
         * `WWW-Authenticate` headers, one per algorithm, allowing the client to choose.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestAuthenticationProvider.Config.algorithms)
         */
        @Suppress("DEPRECATION")
        public var algorithms: List<DigestAlgorithm> = defaultAlgorithms

        /**
         * List of supported Quality of Protection (qop) options.
         *
         * The server can advertise support for:
         * - [DigestQop.AUTH] - Authentication only (default)
         * - [DigestQop.AUTH_INT] - Authentication with integrity protection
         *
         * When [DigestQop.AUTH_INT] is used, the request body is included in the digest
         * calculation, providing integrity protection. Note that using `auth-int` requires
         * reading the request body during authentication. If you need to access the body
         * in your route handler, install the `DoubleReceive` plugin.
         *
         * An empty list means qop is not required.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestAuthenticationProvider.Config.supportedQop)
         */
        public var supportedQop: List<DigestQop> = listOf(DigestQop.AUTH)

        /**
         * The charset to be used. If set to `UTF-8`, it will be passed in the `WWW-Authenticate` header.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestAuthenticationProvider.Config.charset)
         */
        public var charset: Charset? = null

        internal var userHashResolver: UserHashResolverFunction? = null

        /**
         * [NonceManager] to be used to generate nonce values.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestAuthenticationProvider.Config.nonceManager)
         */
        public var nonceManager: NonceManager = GenerateOnlyNonceManager

        /**
         * Sets a validation function that checks a specified [DigestCredential] instance and
         * returns principal [Any] in a case of successful authentication or null if authentication fails.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestAuthenticationProvider.Config.validate)
         */
        public fun validate(body: AuthenticationFunction<DigestCredential>) {
            authenticationFunction = body
        }

        /**
         * Configures a digest provider function that should fetch or compute message digest for the specified
         * `userName` and `realm`. A message digest is usually computed based on username, realm, and password
         * concatenated with the colon character ':'. For example, `"$userName:$realm:$password"`.
         *
         * **Note**: This overload does not receive the algorithm parameter. Consider using the
         * [digestProvider] overload that accepts [DigestAlgorithm] for full RFC 7616 support.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestAuthenticationProvider.Config.digestProvider)
         */
        public fun digestProvider(digest: DigestProviderFunction) {
            digestProvider = { userName, realm, _ -> digest(userName, realm) }
        }

        /**
         * Configures a digest provider function that should fetch or compute message digest for the specified
         * `userName`, `realm`, and `algorithm`.
         *
         * The digest should be computed as `H(username:realm:password)` using the specified algorithm's hash function.
         *
         * @see [DigestAlgorithm] for supported algorithms
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestAuthenticationProvider.Config.digestProvider)
         */
        public fun digestProvider(digest: DigestProviderFunctionV2) {
            digestProvider = digest
        }

        /**
         * Configures a resolver function for userhash support.
         *
         * When a client sends `userhash=true`, the username parameter contains `H(username:realm)`
         * instead of the actual username. This resolver is called to find the actual username
         * from the hash.
         *
         * When set, the server will include `userhash=true` in the WWW-Authenticate challenge header,
         * indicating to clients that they may send hashed usernames.
         *
         * Example implementation using a list of known users:
         * ```kotlin
         * val users = listOf("alice", "bob", "charlie")
         *
         * userHashResolver { userhash, realm, algorithm ->
         *     users.find { username ->
         *         computeUserHash(username, realm, algorithm) == userhash
         *     }
         * }
         * ```
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestAuthenticationProvider.Config.userHashResolver)
         */
        public fun userHashResolver(resolver: UserHashResolverFunction) {
            userHashResolver = resolver
        }

        /**
         * Enables strict RFC 7616 compliance mode by setting the algorithms to SHA-512-256 and SHA-256, and charset to UTF-8.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestAuthenticationProvider.Config.strictRfc7616Mode)
         */
        public fun strictRfc7616Mode() {
            if (algorithms != defaultAlgorithms) {
                LOGGER.warn("Defined algorithms are overridden in strictRfc7616Mode")
            }
            if (charset != null && charset != Charsets.UTF_8) {
                LOGGER.warn("Defined charset is overridden in strictRfc7616Mode")
            }
            algorithms = strictAlgorithms
            charset = Charsets.UTF_8
        }

        internal companion object {
            val strictAlgorithms = listOf(DigestAlgorithm.SHA_512_256, DigestAlgorithm.SHA_256)

            @Suppress("DEPRECATION")
            val defaultAlgorithms = listOf(DigestAlgorithm.SHA_512_256, DigestAlgorithm.MD5)
        }
    }
}

/**
 * Provides a message digest for the specified username and realm or returns `null` if a user is missing.
 * This function could fetch digest from a database or compute it instead.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestProviderFunction)
 */
public typealias DigestProviderFunction = suspend (userName: String, realm: String) -> ByteArray?

/**
 * Provides a message digest for the specified username, realm, and algorithm or returns `null` if a user is missing.
 * This function could fetch digest from a database or compute it instead.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DigestProviderFunctionV2)
 */
public typealias DigestProviderFunctionV2 =
    suspend (userName: String, realm: String, algorithm: DigestAlgorithm) -> ByteArray?

/**
 * Resolves a userhash to the actual username for userhash support.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.UserHashResolverFunction)
 */
public typealias UserHashResolverFunction =
    suspend (userHash: String, realm: String, algorithm: DigestAlgorithm) -> String?

/**
 * Installs the digest [Authentication] provider.
 * To learn how to configure it, see [Digest authentication](https://ktor.io/docs/digest.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.digest)
 */
public fun AuthenticationConfig.digest(
    name: String? = null,
    configure: DigestAuthenticationProvider.Config.() -> Unit
) {
    digest(name, description = null, configure)
}

/**
 * Installs the digest [Authentication] provider with description.
 * To learn how to configure it, see [Digest authentication](https://ktor.io/docs/digest.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.digest)
 */
public fun AuthenticationConfig.digest(
    name: String? = null,
    description: String? = null,
    configure: DigestAuthenticationProvider.Config.() -> Unit
) {
    val provider = DigestAuthenticationProvider(DigestAuthenticationProvider.Config(name, description).apply(configure))
    register(provider)
}

private val digestAuthenticationChallengeKey: Any = "DigestAuth"

/**
 * Retrieves [DigestCredential] for this call.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.digestAuthenticationCredentials)
 */
public fun ApplicationCall.digestAuthenticationCredentials(): DigestCredential? {
    return request.parseAuthorizationHeader()?.let { authHeader ->
        if (authHeader.authScheme == AuthScheme.Digest && authHeader is HttpAuthHeader.Parameterized) {
            return authHeader.toDigestCredential()
        } else {
            null
        }
    }
}

@Deprecated("Maintained binary compatibility", level = DeprecationLevel.HIDDEN)
public fun HttpAuthHeader.Parameterized.toDigestCredential(): DigestCredential =
    toDigestCredential(defaultCharset = Charsets.ISO_8859_1)

/**
 * Verifies that credentials are valid for a given [method], [digester], and [userNameRealmPasswordDigest].
 *
 * This is the legacy verifier that does not support session algorithms or auth-int.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.verifier)
 */
@Deprecated(message = "Use [DigestCredential.verifier] without digester.")
public suspend fun DigestCredential.verifier(
    method: HttpMethod,
    digester: MessageDigest,
    userNameRealmPasswordDigest: suspend (String, String) -> ByteArray?
): Boolean {
    require(digester.algorithm == digestAlgorithm.hashName) { "Wrong digest algorithm" }
    return verifier(method, userNameRealmPasswordDigest)
}

/**
 * Calculates the expected digest bytes for this [DigestCredential].
 *
 * This is the legacy function that does not support session algorithms or auth-int.
 * For full RFC 7616 support, use the overload with an algorithm parameter.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.expectedDigest)
 */
@Deprecated(message = "Use [DigestCredential.expectedDigest] without digester.")
public fun DigestCredential.expectedDigest(
    method: HttpMethod,
    digester: MessageDigest,
    userNameRealmPasswordDigest: ByteArray
): ByteArray {
    require(digestAlgorithm.hashName == digester.algorithm) { "Wrong digest algorithm" }
    return expectedDigest(method, userNameRealmPasswordDigest)
}
