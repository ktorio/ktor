/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth.providers

import io.ktor.client.features.auth.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*

/**
 * Install client [DigestAuthProvider].
 */
public fun Auth.digest(block: DigestAuthConfig.() -> Unit) {
    val config = DigestAuthConfig().apply(block)
    with(config) {
        providers += DigestAuthProvider(_credentials, realm, algorithmName)
    }
}

/**
 * [DigestAuthProvider] configuration.
 */
@Suppress("KDocMissingDocumentation")
public class DigestAuthConfig {

    public var algorithmName: String = "MD5"

    /**
     * Required: The username of the basic auth.
     */
    @Deprecated("Please use `credentials {}` function instead")
    public var username: String = ""

    /**
     * Required: The password of the basic auth.
     */
    @Deprecated("Please use `credentials {}` function instead")
    public var password: String = ""

    /**
     * Optional: current provider realm
     */
    public var realm: String? = null

    internal var _credentials: suspend () -> DigestAuthCredentials? = {
        DigestAuthCredentials(username = username, password = password)
    }

    /**
     * Required: Credentials provider.
     */
    public fun credentials(block: suspend () -> DigestAuthCredentials?) {
        _credentials = block
    }
}

/**
 * Credentials for [DigestAuthProvider].
 */
public class DigestAuthCredentials(
    public val username: String,
    public val password: String
)

/**
 * Client digest [AuthProvider].
 */
@Suppress("KDocMissingDocumentation")
public class DigestAuthProvider(
    private val credentials: suspend () -> DigestAuthCredentials?,
    @Deprecated("This will become private") public val realm: String? = null,
    @Deprecated("This will become private") public val algorithmName: String = "MD5",
) : AuthProvider {

    @Deprecated("Consider using constructor with credentials provider instead")
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

    @Deprecated("This will be removed")
    public val username: String
        get() = error("Static username is not supported anymore")

    @Deprecated("This will be removed")
    public val password: String
        get() = error("Static username is not supported anymore")

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
        if (auth !is HttpAuthHeader.Parameterized ||
            auth.authScheme != AuthScheme.Digest
        ) return false

        val newNonce = auth.parameter("nonce") ?: return false
        val newQop = auth.parameter("qop")
        val newOpaque = auth.parameter("opaque")

        val newRealm = auth.parameter("realm") ?: return false
        if (newRealm != realm && realm != null) {
            return false
        }

        serverNonce.value = newNonce
        qop.value = newQop
        opaque.value = newOpaque

        return true
    }

    override suspend fun addRequestHeaders(request: HttpRequestBuilder) {
        val nonceCount = requestCounter.incrementAndGet()
        val methodName = request.method.value.toUpperCase()
        val url = URLBuilder().takeFrom(request.url).build()

        val nonce = serverNonce.value!!
        val serverOpaque = opaque.value
        val actualQop = qop.value

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
        val realm = realm ?: request.attributes.getOrNull(AuthHeaderAttribute)?.let { auth ->
            (auth as? HttpAuthHeader.Parameterized)?.parameter("realm")
        }

        val auth = HttpAuthHeader.Parameterized(
            AuthScheme.Digest,
            linkedMapOf<String, String>().apply {
                realm?.let { this["realm"] = it }
                serverOpaque?.let { this["opaque"] = it }
                this["username"] = credentials.username
                this["nonce"] = nonce
                this["cnonce"] = clientNonce
                this["response"] = hex(token)
                this["uri"] = url.fullPath
                actualQop?.let { this["qop"] = it }
                this["nc"] = nonceCount.toString()
            }
        )

        request.headers {
            append(HttpHeaders.Authorization, auth.render())
        }
    }

    override suspend fun refreshToken(response: HttpResponse): Boolean {
        tokenHolder.setToken(credentials)
        return true
    }

    private suspend fun makeDigest(data: String): ByteArray {
        val digest = Digest(algorithmName)
        return digest.build(data.toByteArray(Charsets.UTF_8))
    }
}
