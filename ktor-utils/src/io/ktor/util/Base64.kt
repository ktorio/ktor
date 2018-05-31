package io.ktor.util

import kotlinx.io.core.*
import kotlin.experimental.*

private val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
private val BASE64_MASK: Byte = 0x3f
private val BASE64_PAD = '='

private val BASE64_INVERSE_ALPHABET = IntArray(255) {
    BASE64_ALPHABET.indexOf(it.toChar())
}

/**
 * Encode [String] in base64 format
 */
fun String.encodeBase64(): String = buildPacket {
    writeStringUtf8(this@encodeBase64)
}.encodeBase64()

/**
 * Encode [ByteReadPacket] in base64 format
 */
fun ByteReadPacket.encodeBase64(): String = buildString {
    val data = ByteArray(3)
    while (remaining > 0) {
        val read = readAvailable(data)
        data.clearFrom(read)

        val padSize = (data.size - read) * 8 / 6
        val chunk = (data[0].toInt() shl 16) or (data[1].toInt() shl 8) or data[2].toInt()

        for (index in data.size downTo padSize) {
            val char = (chunk shr (6 * index)) and BASE64_MASK.toInt()
            append(char.toBase64())
        }

        repeat(padSize) { append(BASE64_PAD) }
    }
}

/**
 * Decode [String] from base64 format
 */
fun String.decodeBase64(): String = buildPacket {
    writeStringUtf8(dropLastWhile { it == BASE64_PAD })
}.decodeBase64()

/**
 * Decode [ByteReadPacket] from base64 format
 */
fun ByteReadPacket.decodeBase64(): String = buildString {
    val data = ByteArray(4)

    while (remaining > 0) {
        val read = readAvailable(data)

        val chunk = data.foldIndexed(0) { index, result, current ->
            result or (current.fromBase64().toInt() shl ((3 - index) * 6))
        }

        for (index in data.size - 2 downTo (data.size - read)) {
            val origin = (chunk shr (8 * index)) and 0xff
            append(origin.toChar())
        }
    }
}

internal fun ByteArray.clearFrom(from: Int) {
    (from until size).forEach { this[it] = 0 }
}

internal fun Int.toBase64(): Char = BASE64_ALPHABET[this]
internal fun Byte.fromBase64(): Byte = BASE64_INVERSE_ALPHABET[toInt() and 0xff].toByte() and BASE64_MASK

