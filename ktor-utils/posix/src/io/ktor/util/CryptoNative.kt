/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

/**
 * Generates a nonce string 32 characters long. Could block if the system's entropy source is empty
 */
public actual fun generateNonce(): String {
    val bytes = ByteArray(16)
    secureRandom(bytes)
    return hex(bytes)
}

internal expect fun secureRandom(bytes: ByteArray)

/**
 * Create [Digest] from specified hash [name].
 */
public actual fun Digest(name: String): Digest = error("[Digest] is not supported on Darwin")

/**
 * Compute SHA-1 hash for the specified [bytes]
 */
public actual fun sha1(bytes: ByteArray): ByteArray {
    return Sha1().also { it.update(bytes) }.digest()
}

//copy from okio
private interface HashFunction {
    fun update(
        input: ByteArray,
        offset: Int = 0,
        byteCount: Int = input.size
    )

    fun digest(): ByteArray
}

private inline infix fun Int.leftRotate(bitCount: Int): Int {
    return (this shl bitCount) or (this ushr (32 - bitCount))
}

private class Sha1 : HashFunction {
    private var messageLength = 0L
    private val unprocessed = ByteArray(64)
    private var unprocessedLimit = 0
    private val words = IntArray(80)

    private var h0 = 1732584193
    private var h1 = -271733879
    private var h2 = -1732584194
    private var h3 = 271733878
    private var h4 = -1009589776

    override fun update(
        input: ByteArray,
        offset: Int,
        byteCount: Int
    ) {
        messageLength += byteCount
        var pos = offset
        val limit = pos + byteCount
        val unprocessed = this.unprocessed
        val unprocessedLimit = this.unprocessedLimit

        if (unprocessedLimit > 0) {
            if (unprocessedLimit + byteCount < 64) {
                // Not enough bytes for a chunk.
                input.copyInto(unprocessed, unprocessedLimit, pos, limit)
                this.unprocessedLimit = unprocessedLimit + byteCount
                return
            }

            // Process a chunk combining leftover bytes and the input.
            val consumeByteCount = 64 - unprocessedLimit
            input.copyInto(unprocessed, unprocessedLimit, pos, pos + consumeByteCount)
            processChunk(unprocessed, 0)
            this.unprocessedLimit = 0
            pos += consumeByteCount
        }

        while (pos < limit) {
            val nextPos = pos + 64

            if (nextPos > limit) {
                // Not enough bytes for a chunk.
                input.copyInto(unprocessed, 0, pos, limit)
                this.unprocessedLimit = limit - pos
                return
            }

            // Process a chunk.
            processChunk(input, pos)
            pos = nextPos
        }
    }

    private fun processChunk(input: ByteArray, pos: Int) {
        val words = this.words

        var pos = pos
        for (w in 0 until 16) {
            words[w] =
                ((input[pos++].toInt() and 0xff) shl 24) or
                    ((input[pos++].toInt() and 0xff) shl 16) or
                    ((input[pos++].toInt() and 0xff) shl 8) or
                    ((input[pos++].toInt() and 0xff))
        }

        for (w in 16 until 80) {
            words[w] = (words[w - 3] xor words[w - 8] xor words[w - 14] xor words[w - 16]) leftRotate 1
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4

        for (i in 0 until 80) {
            val a2 = when {
                i < 20 -> {
                    val f = d xor (b and (c xor d))
                    val k = 1518500249
                    (a leftRotate 5) + f + e + k + words[i]
                }
                i < 40 -> {
                    val f = b xor c xor d
                    val k = 1859775393
                    (a leftRotate 5) + f + e + k + words[i]
                }
                i < 60 -> {
                    val f = (b and c) or (b and d) or (c and d)
                    val k = -1894007588
                    (a leftRotate 5) + f + e + k + words[i]
                }
                else -> {
                    val f = b xor c xor d
                    val k = -899497514
                    (a leftRotate 5) + f + e + k + words[i]
                }
            }

            e = d
            d = c
            c = b leftRotate 30
            b = a
            a = a2
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
    }

    /* ktlint-disable */
    override fun digest(): ByteArray {
        val unprocessed = this.unprocessed
        var unprocessedLimit = this.unprocessedLimit
        val messageLengthBits = messageLength * 8

        unprocessed[unprocessedLimit++] = 0x80.toByte()
        if (unprocessedLimit > 56) {
            unprocessed.fill(0, unprocessedLimit, 64)
            processChunk(unprocessed, 0)
            unprocessed.fill(0, 0, unprocessedLimit)
        } else {
            unprocessed.fill(0, unprocessedLimit, 56)
        }
        unprocessed[56] = (messageLengthBits ushr 56).toByte()
        unprocessed[57] = (messageLengthBits ushr 48).toByte()
        unprocessed[58] = (messageLengthBits ushr 40).toByte()
        unprocessed[59] = (messageLengthBits ushr 32).toByte()
        unprocessed[60] = (messageLengthBits ushr 24).toByte()
        unprocessed[61] = (messageLengthBits ushr 16).toByte()
        unprocessed[62] = (messageLengthBits ushr 8).toByte()
        unprocessed[63] = (messageLengthBits).toByte()
        processChunk(unprocessed, 0)

        val a = h0
        val b = h1
        val c = h2
        val d = h3
        val e = h4

        reset()

        return byteArrayOf(
            (a shr 24).toByte(),
            (a shr 16).toByte(),
            (a shr 8).toByte(),
            (a).toByte(),
            (b shr 24).toByte(),
            (b shr 16).toByte(),
            (b shr 8).toByte(),
            (b).toByte(),
            (c shr 24).toByte(),
            (c shr 16).toByte(),
            (c shr 8).toByte(),
            (c).toByte(),
            (d shr 24).toByte(),
            (d shr 16).toByte(),
            (d shr 8).toByte(),
            (d).toByte(),
            (e shr 24).toByte(),
            (e shr 16).toByte(),
            (e shr 8).toByte(),
            (e).toByte()
        )
    }
    /* ktlint-enable */

    private fun reset() {
        messageLength = 0L
        unprocessed.fill(0)
        unprocessedLimit = 0
        words.fill(0)

        h0 = 1732584193
        h1 = -271733879
        h2 = -1732584194
        h3 = 271733878
        h4 = -1009589776
    }
}
