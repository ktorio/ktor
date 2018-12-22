package io.ktor.client.features.auth.providers

import io.ktor.client.features.auth.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlinx.io.charsets.*
import kotlinx.io.core.*

/**
 * Install client [DigestAuthProvider].
 */
fun Auth.digest(block: DigestAuthConfig.() -> Unit) {
    val config = DigestAuthConfig().apply(block)
    with(config) {
        providers += DigestAuthProvider(username, password, realm, algorithmName)
    }
}

/**
 * [DigestAuthProvider] configuration.
 */
@Suppress("KDocMissingDocumentation")
class DigestAuthConfig {
    var username: String = ""
    var password: String = ""
    var realm: String? = null
    var algorithmName: String = "MD5"
}

/**
 * Client digest [AuthProvider].
 */
@Suppress("KDocMissingDocumentation")
class DigestAuthProvider(
    val username: String,
    val password: String,
    val realm: String?,
    val algorithmName: String = "MD5"
) : AuthProvider {
    private val digest = Digest(algorithmName)
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
        val end = hex(makeDigest("$methodName:${url.encodedPath}"))
        val tokenSequence = if (actualQop == null) listOf(start, nonce, end) else listOf(start, nonce, nonceCount, clientNonce)
        val token = makeDigest(tokenSequence.joinToString(":"))

        val auth = HttpAuthHeader.Parameterized(AuthScheme.Digest, linkedMapOf<String, String>().apply {
            realm?.let { this["realm"] = it }
            serverOpaque?.let { this["opaque"] = it }
            this["username"] = username
            this["nonce"] = nonce
            this["cnonce"] = clientNonce
            this["response"] = hex(token)
            this["uri"] = url.encodedPath
        })

        request.headers {
            append(HttpHeaders.Authorization, auth.render())
        }
    }

    private val lock = Lock()
    private suspend fun makeDigest(data: String): ByteArray = lock.use {
        digest.reset()
        return@use digest.build(data.toByteArray(Charsets.UTF_8))
    }
}
