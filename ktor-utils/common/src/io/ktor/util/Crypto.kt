@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("CryptoKt")

package io.ktor.util

import kotlinx.io.charsets.*
import kotlinx.io.core.*

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
 * Decode bytes from HEX string. It should be no spaces and `0x` prefixes.
 */
@KtorExperimentalAPI
fun hex(s: String): ByteArray {
    val result = ByteArray(s.length / 2)
    for (idx in 0 until result.size) {
        val srcIdx = idx * 2
        val high = s[srcIdx].toString().toInt(16) shl 4
        val low = s[srcIdx + 1].toString().toInt(16)
        result[idx] = (high or low).toByte()
    }

    return result
}

@InternalAPI
expect fun generateNonce(): String

@InternalAPI
fun generateNonce(size: Int): ByteArray = buildPacket {
    while (this.size < size) {
        writeStringUtf8(generateNonce())
    }
}.readBytes(size)

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
@KtorExperimentalAPI
expect fun sha1(bytes: ByteArray): ByteArray

@InternalAPI
expect fun Digest(name: String): Digest

@InternalAPI
interface Digest {
    operator fun plusAssign(bytes: ByteArray)

    fun reset()

    suspend fun build(): ByteArray
}

@InternalAPI
suspend fun Digest.build(bytes: ByteArray): ByteArray {
    this += bytes
    return build()
}

@InternalAPI
suspend fun Digest.build(string: String, charset: Charset = Charsets.UTF_8): ByteArray {
    this += string.toByteArray(charset)
    return build()
}
