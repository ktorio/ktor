/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth.providers

import io.ktor.client.features.auth.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*
import kotlinx.atomicfu.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*

/**
 * Install client [DigestAuthProvider].
 */
public fun Auth.digest(block: DigestAuthConfig.() -> Unit) {
    val config = DigestAuthConfig().apply(block)
    with(config) {
        providers += DigestAuthProvider(username, password, realm, algorithmName)
    }
}

/**
 * [DigestAuthProvider] configuration.
 */
@Suppress("KDocMissingDocumentation")
public class DigestAuthConfig {
    public var username: String = ""
    public var password: String = ""
    public var realm: String? = null
    public var algorithmName: String = "MD5"
}

/**
 * Client digest [AuthProvider].
 */
@Suppress("KDocMissingDocumentation")
public class DigestAuthProvider(
    public val username: String,
    public val password: String,
    public val realm: String?,
    public val algorithmName: String = "MD5"
) : AuthProvider {
    override val sendWithoutRequest: Boolean = false

    private val serverNonce = atomic<String?>(null)
    private val qop = atomic<String?>(null)
    private val opaque = atomic<String?>(null)
    private val clientNonce = generateNonce()

    private val requestCounter = atomic(0)

    override fun isApplicable(auth: HttpAuthHeader): Boolean {
        if (auth !is HttpAuthHeader.Parameterized ||
            auth.parameter("realm") != realm ||
            auth.authScheme != AuthScheme.Digest
        ) return false

        val newNonce = auth.parameter("nonce") ?: return false
        val newQop = auth.parameter("qop")
        val newOpaque = auth.parameter("opaque")

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

        val credential = makeDigest("$username:$realm:$password")

        val start = hex(credential)
        val end = hex(makeDigest("$methodName:${url.fullPath}"))
        val tokenSequence = if (actualQop == null) listOf(start, nonce, end) else listOf(start, nonce, nonceCount, clientNonce, actualQop, end)
        val token = makeDigest(tokenSequence.joinToString(":"))

        val auth = HttpAuthHeader.Parameterized(AuthScheme.Digest, linkedMapOf<String, String>().apply {
            realm?.let { this["realm"] = it }
            serverOpaque?.let { this["opaque"] = it }
            this["username"] = username
            this["nonce"] = nonce
            this["cnonce"] = clientNonce
            this["response"] = hex(token)
            this["uri"] = url.fullPath
            actualQop?.let { this["qop"] = it }
            this["nc"] = nonceCount.toString()
        })

        request.headers {
            append(HttpHeaders.Authorization, auth.render())
        }
    }

    private suspend fun makeDigest(data: String): ByteArray {
        val digest = Digest(algorithmName)
        return digest.build(data.toByteArray(Charsets.UTF_8))
    }
}
