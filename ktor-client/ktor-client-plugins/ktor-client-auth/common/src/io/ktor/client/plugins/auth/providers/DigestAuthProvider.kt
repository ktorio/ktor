/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth.providers

import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*
import io.ktor.util.logging.trace
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.locks.*
import kotlinx.atomicfu.atomic

/**
 * Installs the client's [DigestAuthProvider].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.digest)
 */
public fun AuthConfig.digest(block: DigestAuthConfig.() -> Unit) {
    val config = DigestAuthConfig().apply(block)
    with(config) {
        this@digest.providers += DigestAuthProvider(credentials, realm, algorithmName)
    }
}

/**
 * A configuration for [DigestAuthProvider].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.DigestAuthConfig)
 */
@KtorDsl
public class DigestAuthConfig {

    public var algorithmName: String = "MD5"

    /**
     * Required: The username of the basic auth.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.DigestAuthConfig.username)
     */
    @Deprecated("Please use `credentials {}` function instead", level = DeprecationLevel.ERROR)
    public var username: String = ""

    /**
     * Required: The password of the basic auth.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.DigestAuthConfig.password)
     */
    @Deprecated("Please use `credentials {}` function instead", level = DeprecationLevel.ERROR)
    public var password: String = ""

    /**
     * (Optional) Specifies the realm of the current provider.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.DigestAuthConfig.realm)
     */
    public var realm: String? = null

    @Suppress("DEPRECATION_ERROR")
    internal var credentials: suspend () -> DigestAuthCredentials? = {
        DigestAuthCredentials(username = username, password = password)
    }

    /**
     * Allows you to specify authentication credentials.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.DigestAuthConfig.credentials)
     */
    public fun credentials(block: suspend () -> DigestAuthCredentials?) {
        credentials = block
    }
}

/**
 * Contains credentials for [DigestAuthProvider].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.DigestAuthCredentials)
 */
public class DigestAuthCredentials(
    public val username: String,
    public val password: String
)

/**
 * An authentication provider for the Digest HTTP authentication scheme.
 *
 * You can learn more from [Digest authentication](https://ktor.io/docs/digest-client.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.DigestAuthProvider)
 */
public class DigestAuthProvider(
    private val credentials: suspend () -> DigestAuthCredentials?,
    @Deprecated("This will become private", level = DeprecationLevel.ERROR) public val realm: String? = null,
    @Deprecated("This will become private", level = DeprecationLevel.ERROR) public val algorithmName: String = "MD5",
) : AuthProvider {

    @Deprecated("Consider using constructor with credentials provider instead", level = DeprecationLevel.ERROR)
    public constructor(
        username: String,
        password: String,
        realm: String? = null,
        algorithmName: String = "MD5"
    ) : this(
        credentials = { DigestAuthCredentials(username = username, password = password) },
        realm = realm,
        algorithmName = algorithmName
    )

    @Suppress("OverridingDeprecatedMember")
    @Deprecated("Please use sendWithoutRequest function instead", level = DeprecationLevel.ERROR)
    override val sendWithoutRequest: Boolean
        get() = error("Deprecated")

    private val clientNonce = atomic<String?>(null)

    // RFC 7616 §3.4: "nc" counts the requests sent with a particular server nonce.
    // Counters are scoped by protection space, so a server issuing a fresh nonce with every challenge
    // can't evict the counters of other servers or realms.
    @OptIn(InternalAPI::class)
    private val nonceCountsLock = SynchronizedObject()
    private val nonceCounts = LinkedHashMap<ProtectionSpace, LinkedHashMap<String, Int>>()

    private val tokenHolder = AuthTokenHolder(credentials)

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = false

    override fun isApplicable(auth: HttpAuthHeader): Boolean = parseChallenge(auth) != null

    // The provider is shared between concurrent requests, so the challenge is never stored:
    // each request is signed with the challenge from its own 401 response.
    private fun parseChallenge(auth: HttpAuthHeader): DigestChallenge? {
        if (auth !is HttpAuthHeader.Parameterized || auth.authScheme != AuthScheme.Digest) {
            LOGGER.trace { "Digest Auth Provider is not applicable for $auth" }
            return null
        }

        val nonce = auth.parameter("nonce") ?: run {
            LOGGER.trace { "Digest Auth Provider can not handle response without nonce parameter" }
            return null
        }

        val challengeRealm = auth.parameter("realm") ?: run {
            LOGGER.trace { "Digest Auth Provider can not handle response without realm parameter" }
            return null
        }
        @Suppress("DEPRECATION_ERROR")
        if (challengeRealm != realm && realm != null) {
            LOGGER.trace { "Digest Auth Provider is not applicable for this realm" }
            return null
        }

        // Per RFC 7616 §3.3: a missing algorithm parameter defaults to MD5.
        val challengeAlgorithm = auth.parameter("algorithm") ?: "MD5"
        @Suppress("DEPRECATION_ERROR")
        if (!challengeAlgorithm.equals(algorithmName, ignoreCase = true)) {
            LOGGER.trace { "Digest Auth Provider is not applicable for algorithm $challengeAlgorithm" }
            return null
        }

        // RFC 7616 §3.4: the client must choose one of the offered qop values; only "auth" is supported.
        val offeredQop = auth.parameter("qop")
        if (offeredQop != null && offeredQop.split(',').none { it.trim().equals(QOP_AUTH, ignoreCase = true) }) {
            LOGGER.trace { "Digest Auth Provider does not support any of the offered qop values: $offeredQop" }
            return null
        }

        return DigestChallenge(
            nonce = nonce,
            realm = challengeRealm,
            qop = offeredQop?.let { QOP_AUTH },
            opaque = auth.parameter("opaque"),
        )
    }

    private suspend inline fun getNonce(): String {
        clientNonce.value?.let { return it }
        val newNonce = generateNonceSuspend()
        val prevNonce = clientNonce.getAndSet(newNonce)
        return prevNonce ?: newNonce
    }

    @OptIn(InternalAPI::class)
    private fun nextNonceCount(space: ProtectionSpace, nonce: String): Int = synchronized(nonceCountsLock) {
        // Re-inserting keys keeps the least recently used ones first in line for eviction
        val spaceCounts = nonceCounts.remove(space) ?: LinkedHashMap()
        nonceCounts.putMostRecent(key = space, value = spaceCounts, maxCapacity = MAX_TRACKED_PROTECTION_SPACES)

        val nextCount = (spaceCounts.remove(nonce) ?: 0) + 1
        spaceCounts.putMostRecent(key = nonce, value = nextCount, maxCapacity = MAX_NONCES_PER_PROTECTION_SPACE)
        nextCount
    }

    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        val challenge = authHeader?.let(::parseChallenge) ?: run {
            LOGGER.trace { "Digest Auth Provider can not add header: no Digest challenge was received" }
            return
        }

        val methodName = request.method.value.uppercase()
        val url = URLBuilder().takeFrom(request.url).build()

        val nonce = challenge.nonce
        val serverOpaque = challenge.opaque
        val actualQop = challenge.qop
        val realm = challenge.realm

        val credentials = tokenHolder.loadToken() ?: return
        // RFC 2617 §3.2.2: nc and cnonce are sent only with qop.
        // Though RFC 7616 requires qop, we support older servers.
        val nonceCount = actualQop?.let {
            val protectionSpace = ProtectionSpace(url.protocol, url.host, url.port, realm)
            nextNonceCount(protectionSpace, nonce).toString(radix = 16).padStart(length = 8, padChar = '0')
        }
        val cnonce = actualQop?.let { getNonce() }
        val credential = makeDigest("${credentials.username}:$realm:${credentials.password}")

        // Clients send "/" when the URL path is empty (RFC 9112); digest uri/HA2 must match that target.
        val requestTarget = if (url.rawSegments.isEmpty()) "/${url.fullPath}" else url.fullPath

        val start = credential.toHexString()
        val end = makeDigest("$methodName:$requestTarget").toHexString()
        // nonceCount, cnonce and actualQop are either all set or all null
        val tokenSequence = listOfNotNull(start, nonce, nonceCount, cnonce, actualQop, end)

        val token = makeDigest(tokenSequence.joinToString(":"))

        val auth = HttpAuthHeader.Parameterized(
            authScheme = AuthScheme.Digest,
            parameters = linkedMapOf<String, String>().apply {
                this["realm"] = realm.quote()
                serverOpaque?.let { this["opaque"] = it.quote() }
                this["username"] = credentials.username.quote()
                this["nonce"] = nonce.quote()
                cnonce?.let { this["cnonce"] = it.quote() }
                this["response"] = token.toHexString().quote()
                this["uri"] = requestTarget.quote()
                actualQop?.let { this["qop"] = it }
                nonceCount?.let { this["nc"] = it }
                @Suppress("DEPRECATION_ERROR")
                this["algorithm"] = algorithmName
            },
            encoding = HeaderValueEncoding.QUOTED_WHEN_REQUIRED
        )

        request.headers {
            append(HttpHeaders.Authorization, auth.render())
        }
    }

    override suspend fun refreshToken(response: HttpResponse): Boolean {
        tokenHolder.setToken(false, credentials)
        return true
    }

    @Suppress("DEPRECATION_ERROR")
    @OptIn(InternalAPI::class)
    private suspend fun makeDigest(data: String): ByteArray {
        val digest = Digest(algorithmName)
        return digest.build(data.toByteArray(Charsets.UTF_8))
    }

    /**
     * Clears the currently stored authentication tokens from the cache.
     *
     * This method should be called in the following cases:
     * - When the credentials have been updated and need to take effect
     * - When you want to clear sensitive authentication data
     *
     * Note: The result of [credentials] invocation is cached internally.
     * Calling this method will force the next authentication attempt to fetch fresh credentials.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.auth.providers.DigestAuthProvider.clearToken)
     */
    public override fun clearToken() {
        tokenHolder.clearToken()
    }
}

/**
 * Parameters of a Digest `WWW-Authenticate` challenge accepted by [DigestAuthProvider].
 */
private class DigestChallenge(
    val nonce: String,
    val realm: String,
    val qop: String?,
    val opaque: String?,
)

/**
 * RFC 7235 §2.2 protection space: the canonical root URI of the server combined with the realm.
 */
private data class ProtectionSpace(
    val protocol: URLProtocol,
    val host: String,
    val port: Int,
    val realm: String,
)

/**
 * Inserts [key], which must not be present, as the most recently used entry.
 * Evicts the least recently used entry when the map already holds [maxCapacity] entries.
 */
private fun <K, V> LinkedHashMap<K, V>.putMostRecent(key: K, value: V, maxCapacity: Int) {
    if (size >= maxCapacity) remove(keys.first())
    put(key, value)
}

// Bounds memory for clients talking to many servers; only the least recently used server loses its counters
private const val MAX_TRACKED_PROTECTION_SPACES = 64

// A server can have several nonces in use at once: previous nonces while switching to a new one,
// or several long-lived ones, e.g. from load-balanced backends
private const val MAX_NONCES_PER_PROTECTION_SPACE = 16

private const val QOP_AUTH = "auth"
