/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.coroutines.*
import org.khronos.webgl.*
import kotlin.js.*

/**
 * Generates a nonce string.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.generateNonce)
 */
public actual fun generateNonce(): String {
    val buffer = ByteArray(NONCE_SIZE_IN_BYTES).toJsArray()
    _crypto.getRandomValues(buffer)
    return hex(buffer.toByteArray())
}

/**
 * Create [Digest] from specified hash [name].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.Digest)
 */
public actual fun Digest(name: String): Digest = object : Digest {
    private val state = mutableListOf<ByteArray>()
    override fun plusAssign(bytes: ByteArray) {
        state += bytes
    }

    override fun reset() {
        state.clear()
    }

    override suspend fun build(): ByteArray {
        val combined = state.reduce { a, b -> a + b }
        val digestBuffer = try {
            _crypto.subtle.digest(name, combined.toJsArray()).awaitBuffer()
        } catch (e: JsException) {
            // Browser SubtleCrypto excludes MD5 for security reasons; fall back to a pure Kotlin implementation.
            if (name == "MD5") {
                md5(combined)
            } else {
                throw e
            }
        }
        val digestView = DataView(digestBuffer)
        return ByteArray(digestView.byteLength) { digestView.getUint8(it) }
    }
}

// Variable is renamed to `_crypto` so it wouldn't clash with existing `crypto` variable.
// JS IR backend doesn't reserve names accessed inside js("") calls
@Suppress("ObjectPropertyName")
private val _crypto: Crypto = js("(globalThis ? globalThis.crypto : (window.crypto || window.msCrypto))")

private external class Crypto {
    val subtle: SubtleCrypto

    fun getRandomValues(array: Int8Array)
}

private external class SubtleCrypto {
    fun digest(algoName: String, buffer: Int8Array): Promise<ArrayBuffer>
}

/**
 * Compute SHA-1 hash for the specified [bytes]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.sha1)
 */
public actual fun sha1(bytes: ByteArray): ByteArray = Sha1().digest(bytes)

internal fun md5(input: ByteArray): ArrayBuffer {
    var a = 0x67452301
    var b = 0xefcdab89.toInt()
    var c = 0x98badcfe.toInt()
    var d = 0x10325476

    val numZeros = (56 - (input.size + 1) % 64 + 64) % 64
    val padded = ByteArray(input.size + 1 + numZeros + 8)
    input.copyInto(padded)
    padded[input.size] = 0x80.toByte()
    val messageLengthBits = input.size.toLong() * 8
    val lengthOffset = input.size + 1 + numZeros
    for (i in 0 until 8) padded[lengthOffset + i] = (messageLengthBits ushr (i * 8)).toByte()

    val m = IntArray(16)
    for (chunkOffset in padded.indices step 64) {
        for (i in 0 until 16) {
            val j = chunkOffset + i * 4
            m[i] = (padded[j].toInt() and 0xff) or
                ((padded[j + 1].toInt() and 0xff) shl 8) or
                ((padded[j + 2].toInt() and 0xff) shl 16) or
                ((padded[j + 3].toInt() and 0xff) shl 24)
        }

        var aa = a
        var bb = b
        var cc = c
        var dd = d
        var f: Int
        var g: Int
        for (i in 0 until 64) {
            when {
                i < 16 -> {
                    f = (bb and cc) or (bb.inv() and dd)
                    g = i
                }
                i < 32 -> {
                    f = (dd and bb) or (dd.inv() and cc)
                    g = (5 * i + 1) % 16
                }
                i < 48 -> {
                    f = bb xor cc xor dd
                    g = (3 * i + 5) % 16
                }
                else -> {
                    f = cc xor (bb or dd.inv())
                    g = (7 * i) % 16
                }
            }
            val temp = dd
            dd = cc
            cc = bb
            val sum = aa + f + md5T[i] + m[g]
            val s = md5Shifts[i]
            bb += (sum shl s) or (sum ushr (32 - s))
            aa = temp
        }
        a += aa
        b += bb
        c += cc
        d += dd
    }

    val result = ByteArray(16)
    for (i in 0 until 4) result[i] = (a ushr (i * 8)).toByte()
    for (i in 0 until 4) result[4 + i] = (b ushr (i * 8)).toByte()
    for (i in 0 until 4) result[8 + i] = (c ushr (i * 8)).toByte()
    for (i in 0 until 4) result[12 + i] = (d ushr (i * 8)).toByte()
    return result.toJsArray().buffer
}

private val md5Shifts = intArrayOf(
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
)

private val md5T = intArrayOf(
    0xd76aa478.toInt(), 0xe8c7b756.toInt(), 0x242070db, 0xc1bdceee.toInt(),
    0xf57c0faf.toInt(), 0x4787c62a, 0xa8304613.toInt(), 0xfd469501.toInt(),
    0x698098d8, 0x8b44f7af.toInt(), 0xffff5bb1.toInt(), 0x895cd7be.toInt(),
    0x6b901122, 0xfd987193.toInt(), 0xa679438e.toInt(), 0x49b40821,
    0xf61e2562.toInt(), 0xc040b340.toInt(), 0x265e5a51, 0xe9b6c7aa.toInt(),
    0xd62f105d.toInt(), 0x02441453, 0xd8a1e681.toInt(), 0xe7d3fbc8.toInt(),
    0x21e1cde6, 0xc33707d6.toInt(), 0xf4d50d87.toInt(), 0x455a14ed,
    0xa9e3e905.toInt(), 0xfcefa3f8.toInt(), 0x676f02d9, 0x8d2a4c8a.toInt(),
    0xfffa3942.toInt(), 0x8771f681.toInt(), 0x6d9d6122, 0xfde5380c.toInt(),
    0xa4beea44.toInt(), 0x4bdecfa9, 0xf6bb4b60.toInt(), 0xbebfbc70.toInt(),
    0x289b7ec6, 0xeaa127fa.toInt(), 0xd4ef3085.toInt(), 0x04881d05,
    0xd9d4d039.toInt(), 0xe6db99e5.toInt(), 0x1fa27cf8, 0xc4ac5665.toInt(),
    0xf4292244.toInt(), 0x432aff97, 0xab9423a7.toInt(), 0xfc93a039.toInt(),
    0x655b59c3, 0x8f0ccc92.toInt(), 0xffeff47d.toInt(), 0x85845dd1.toInt(),
    0x6fa87e4f, 0xfe2ce6e0.toInt(), 0xa3014314.toInt(), 0x4e0811a1,
    0xf7537e82.toInt(), 0xbd3af235.toInt(), 0x2ad7d2bb, 0xeb86d391.toInt()
)
