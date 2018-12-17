@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("CryptoKt")
package io.ktor.util

import kotlinx.coroutines.*
import java.security.*
import java.util.*

/**
 * Create a digest function with the specified [algorithm] and [salt]
 */
@KtorExperimentalAPI
fun getDigestFunction(algorithm: String, salt: String): (String) -> ByteArray = { e -> getDigest(e, algorithm, salt) }

private fun getDigest(text: String, algorithm: String, salt: String): ByteArray =
    with(MessageDigest.getInstance(algorithm)) {
        update(salt.toByteArray())
        digest(text.toByteArray())
    }

/**
 * Decode bytes from a BASE64 string [s]
 */
@InternalAPI
fun decodeBase64(s: String): ByteArray = Base64.getDecoder().decode(s)

/**
 * Encode [bytes] as a BASE64 string
 */
@InternalAPI
fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
@KtorExperimentalAPI
fun sha1(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA1").digest(bytes)!!

@InternalAPI
actual fun Digest(name: String): Digest = object : Digest {
    private val delegate = MessageDigest.getInstance(name)

    override fun plusAssign(bytes: ByteArray) {
        delegate.update(bytes)
    }

    override fun reset() {
        delegate.reset()
    }

    override suspend fun build(): ByteArray = delegate.digest()
}

/**
 * Encode string as UTF-8 bytes
 */
@KtorExperimentalAPI
@Deprecated("Will be removed in future releases", ReplaceWith("s.toByteArray(Charsets.UTF_8)"))
fun raw(s: String) = s.toByteArray(Charsets.UTF_8)

@Suppress("KDocMissingDocumentation", "unused")
@Deprecated("Use generateNonce() instead")
val nonceRandom: Random by lazy { SecureRandom() }

/**
 * Generates a nonce string 16 characters long. Could block if the system's entropy source is empty
 */
@KtorExperimentalAPI
@Deprecated("Use generateNonce() instead", ReplaceWith("generateNonce()"))
fun nextNonce(): String = generateNonce()

/**
 * Generates a nonce string 16 characters long. Could block if the system's entropy source is empty
 */
@KtorExperimentalAPI
actual fun generateNonce(): String {
    val nonce = seedChannel.poll()
    if (nonce != null) return nonce

    return generateNonceBlocking()
}

private fun generateNonceBlocking(): String {
    ensureNonceGeneratorRunning()
    return runBlocking {
        seedChannel.receive()
    }
}

