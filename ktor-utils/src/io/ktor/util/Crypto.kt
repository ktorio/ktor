package io.ktor.util

import java.security.*
import java.util.*

fun getDigestFunction(algorithm: String, salt: String): (String) -> ByteArray = { e -> getDigest(e, algorithm, salt) }

private fun getDigest(text: String, algorithm: String, salt: String): ByteArray = with(MessageDigest.getInstance(algorithm)) {
    update(salt.toByteArray())
    digest(text.toByteArray())
}

fun decodeBase64(s: String): ByteArray = Base64.getDecoder().decode(s)
fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

fun sha1(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA1").digest(bytes)!!

// useful to work with openssl command line tool
fun hex(s: String): ByteArray {
    val result = ByteArray(s.length / 2)
    for (idx in 0 until result.size) {
        val srcIdx = idx * 2
        result[idx] = ((Integer.parseInt(s[srcIdx].toString(), 16)) shl 4 or Integer.parseInt(s[srcIdx + 1].toString(), 16)).toByte()
    }

    return result
}

fun hex(bytes: ByteArray) = bytes.joinToString("") {
    Integer.toHexString(it.toInt() and 0xff).padStart(2, '0')
}

fun raw(s: String) = s.toByteArray(Charsets.UTF_8)

val nonceRandom by lazy { Random(SecureRandom().nextLong()).apply {
    repeat((System.currentTimeMillis() % 17).toInt()) {
        nextGaussian()
    }
} }

fun nextNonce(): String =
        java.lang.Long.toHexString(nonceRandom.nextLong()) +
                java.lang.Long.toHexString(nonceRandom.nextLong()) +
                java.lang.Long.toHexString(nonceRandom.nextLong()) +
                java.lang.Long.toHexString(nonceRandom.nextLong())
