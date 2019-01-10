package io.ktor.auth

import io.ktor.util.*
import java.util.concurrent.*
import javax.crypto.*
import javax.crypto.spec.*

/**
 * Represents a nonce manager. It's responsibility is to produce nonce values
 * and verify nonce values from untrusted sources that they are provided by this manager.
 * This is usually required in web environment to mitigate CSRF attacks.
 * Depending on it's underlying implementation it could be stateful or stateless.
 * Note that there is usually some timeout for nonce values to reduce memory usage and to avoid replay attacks.
 * Nonce length is unspecified.
 */
@KtorExperimentalAPI
interface NonceManager {
    /**
     * Generate new nonce instance
     */
    suspend fun newNonce(): String

    /**
     * Verify [nonce] value
     * @return `true` if [nonce] is valid
     */
    suspend fun verifyNonce(nonce: String): Boolean
}

/**
 * This implementation does only generate nonce values but doesn't validate them. This is recommended for testing only.
 */
@KtorExperimentalAPI
object GenerateOnlyNonceManager : NonceManager {
    override suspend fun newNonce(): String {
        return generateNonce()
    }

    override suspend fun verifyNonce(nonce: String): Boolean {
        return true
    }
}

@Deprecated("This should be removed with OAuth2StateProvider")
internal object AlwaysFailNonceManager : NonceManager {
    override suspend fun newNonce(): String {
        throw UnsupportedOperationException("This manager should never be used")
    }

    override suspend fun verifyNonce(nonce: String): Boolean {
        throw UnsupportedOperationException("This manager should never be used")
    }
}

/**
 * Stateless nonce manager implementation with HMAC verification and timeout.
 * Every nonce provided by this manager consist of a random part, timestamp and HMAC.
 * @property keySpec secret key spec for HMAC
 * @property algorithm HMAC algorithm name, `HmacSHA256` by default
 * @property timeoutMillis specifies the amount of time for a nonce to be considered valid
 * @property nonceGenerator function that produces random values
 */
@KtorExperimentalAPI
class StatelessNonceManager(
    val keySpec: SecretKeySpec,
    val algorithm: String = "HmacSHA256",
    val timeoutMillis: Long = 60000,
    val nonceGenerator: () -> String = { generateNonce() }
) : NonceManager {
    /**
     * Helper constructor that makes a secret key from [key] ByteArray
     */
    constructor(
        key: ByteArray,
        algorithm: String = "HmacSHA256",
        timeoutMillis: Long = 60000,
        nonceGenerator: () -> String = { generateNonce() }
    ) : this(
        SecretKeySpec(
            key,
            algorithm
        ), algorithm, timeoutMillis, nonceGenerator
    )

    /**
     * MAC length in bytes
     */
    private val macLength = Mac.getInstance(algorithm).let { mac ->
        mac.init(keySpec)
        mac.macLength
    }

    override suspend fun newNonce(): String {
        val random = nonceGenerator()
        val time = System.nanoTime().toString(16).padStart(16, '0')

        val mac = hex(Mac.getInstance(algorithm).apply {
            init(keySpec)
            update("$random:$time".toByteArray(Charsets.ISO_8859_1))
        }.doFinal())

        return "$random+$time+$mac"
    }

    override suspend fun verifyNonce(nonce: String): Boolean {
        val parts = nonce.split('+')
        if (parts.size != 3) return false
        val (random, time, mac) = parts

        if (random.length < 8) return false
        if (mac.length != macLength * 2) return false
        if (time.length != 16) return false

        val nanoTime = time.toLong(16)
        if (nanoTime + TimeUnit.MILLISECONDS.toNanos(timeoutMillis) < System.nanoTime()) return false

        val computedMac = hex(Mac.getInstance(algorithm).apply {
            init(keySpec)
            update("$random:$time".toByteArray(Charsets.ISO_8859_1))
        }.doFinal())

        var validCount = 0
        for (i in 0 until minOf(computedMac.length, mac.length)) {
            if (computedMac[i] == mac[i]) {
                validCount++
            }
        }

        return validCount == macLength * 2
    }
}
