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

/**
 * Decode bytes from HEX string. It should be no spaces and `0x` prefixes.
 */
@KtorExperimentalAPI
fun hex(s: String): ByteArray {
    val result = ByteArray(s.length / 2)
    for (idx in 0 until result.size) {
        val srcIdx = idx * 2
        result[idx] = ((Integer.parseInt(s[srcIdx].toString(), 16)) shl 4 or Integer.parseInt(
            s[srcIdx + 1].toString(),
            16
        )).toByte()
    }

    return result
}

private val digits = "0123456789abcdef".toCharArray()

/**
 * Encode [bytes] as a HEX string with no spaces, newlines and `0x` prefixes.
 */
@KtorExperimentalAPI
fun hex(bytes: ByteArray): String {
    val result = CharArray(bytes.size * 2)
    var resultIndex = 0
    val digits = digits

    for (index in 0 until bytes.size) {
        val b = bytes[index].toInt() and 0xff
        result[resultIndex++] = digits[b shr 4]
        result[resultIndex++] = digits[b and 0x0f]
    }

    return String(result)
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
fun generateNonce(): String {
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

