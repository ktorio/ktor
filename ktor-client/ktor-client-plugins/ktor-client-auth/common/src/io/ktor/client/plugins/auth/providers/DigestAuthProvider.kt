/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth.providers

import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*

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

    private val serverNonce = atomic<String?>(null)

    private val qop = atomic<String?>(null)
    private val opaque = atomic<String?>(null)
    private val clientNonce = generateNonce()

    private val requestCounter = atomic(0)

    private val tokenHolder = AuthTokenHolder(credentials)

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = false

    override fun isApplicable(auth: HttpAuthHeader): Boolean {
        if (auth !is HttpAuthHeader.Parameterized || auth.authScheme != AuthScheme.Digest) {
            LOGGER.trace("Digest Auth Provider is not applicable for $auth")
            return false
        }

        val newNonce = auth.parameter("nonce") ?: run {
            LOGGER.trace("Digest Auth Provider can not handle response without nonce parameter")
            return false
        }
        val newQop = auth.parameter("qop")
        val newOpaque = auth.parameter("opaque")

        val newRealm = auth.parameter("realm") ?: run {
            LOGGER.trace("Digest Auth Provider can not handle response without realm parameter")
            return false
        }
        @Suppress("DEPRECATION_ERROR")
        if (newRealm != realm && realm != null) {
            LOGGER.trace("Digest Auth Provider is not applicable for this realm")
            return false
        }

        serverNonce.value = newNonce
        qop.value = newQop
        opaque.value = newOpaque

        return true
    }

    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        val nonceCount = requestCounter.incrementAndGet().toString(radix = 16).padStart(length = 8, padChar = '0')
        val methodName = request.method.value.uppercase()
        val url = URLBuilder().takeFrom(request.url).build()

        val nonce = serverNonce.value!!
        val serverOpaque = opaque.value
        val actualQop = qop.value

        @Suppress("DEPRECATION_ERROR")
        val realm = realm ?: authHeader?.let { auth ->
            (auth as? HttpAuthHeader.Parameterized)?.parameter("realm")
        }

        val credentials = tokenHolder.loadToken() ?: return
        val credential = makeDigest("${credentials.username}:$realm:${credentials.password}")

        val start = hex(credential)
        val end = hex(makeDigest("$methodName:${url.fullPath}"))
        val tokenSequence = if (actualQop == null) {
            listOf(start, nonce, end)
        } else {
            listOf(start, nonce, nonceCount, clientNonce, actualQop, end)
        }

        val token = makeDigest(tokenSequence.joinToString(":"))

        val auth = HttpAuthHeader.Parameterized(
            authScheme = AuthScheme.Digest,
            parameters = linkedMapOf<String, String>().apply {
                realm?.let { this["realm"] = it.quote() }
                serverOpaque?.let { this["opaque"] = it.quote() }
                this["username"] = credentials.username.quote()
                this["nonce"] = nonce.quote()
                this["cnonce"] = clientNonce.quote()
                this["response"] = hex(token).quote()
                this["uri"] = url.fullPath.quote()
                actualQop?.let { this["qop"] = it }
                this["nc"] = nonceCount
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
        tokenHolder.setToken(credentials)
        return true
    }

    @Suppress("DEPRECATION_ERROR")
    @OptIn(InternalAPI::class)
    private suspend fun makeDigest(data: String): ByteArray {
        val digest = Digest(algorithmName)
        return digest.build(data.toByteArray(Charsets.UTF_8))
    }
}
