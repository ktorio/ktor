/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

internal interface HashFunction {
    fun update(input: ByteArray, offset: Int = 0, length: Int = input.size)
    fun digest(): ByteArray
}

internal fun HashFunction.digest(input: ByteArray, offset: Int = 0, length: Int = input.size): ByteArray {
    update(input, offset, length)
    return digest()
}

private infix fun Int.leftRotate(bitCount: Int): Int {
    return (this shl bitCount) or (this ushr (32 - bitCount))
}

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar
private infix fun Byte.and(other: Int): Int = toInt() and other

internal class Sha1 : HashFunction {
    private var messageLength = 0L
    private val unprocessed = ByteArray(64)
    private var unprocessedLimit = 0
    private val words = IntArray(80)

    private var h0 = 1732584193
    private var h1 = -271733879
    private var h2 = -1732584194
    private var h3 = 271733878
    private var h4 = -1009589776

    override fun update(input: ByteArray, offset: Int, length: Int) {
        messageLength += length
        var pos = offset
        val limit = pos + length
        val unprocessed = this.unprocessed
        val unprocessedLimit = this.unprocessedLimit

        if (unprocessedLimit > 0) {
            if (unprocessedLimit + length < 64) {
                // Not enough bytes for a chunk.
                input.copyInto(unprocessed, unprocessedLimit, pos, limit)
                this.unprocessedLimit = unprocessedLimit + length
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

        var currentPosition = pos
        for (w in 0 until 16) {
            words[w] =
                ((input[currentPosition++].toInt() and 0xff) shl 24) or
                ((input[currentPosition++].toInt() and 0xff) shl 16) or
                ((input[currentPosition++].toInt() and 0xff) shl 8) or
                ((input[currentPosition++].toInt() and 0xff))
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

internal class Sha256 : HashFunction {
    private var messageLength = 0L
    private val unprocessed = ByteArray(64)
    private var unprocessedLimit = 0
    private val words = IntArray(64)

    private var h0 = 1779033703
    private var h1 = -1150833019
    private var h2 = 1013904242
    private var h3 = -1521486534
    private var h4 = 1359893119
    private var h5 = -1694144372
    private var h6 = 528734635
    private var h7 = 1541459225

    override fun update(
        input: ByteArray,
        offset: Int,
        byteCount: Int,
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

        var cPos = pos
        for (w in 0 until 16) {
            words[w] = ((input[cPos++] and 0xff) shl 24) or
                ((input[cPos++] and 0xff) shl 16) or
                ((input[cPos++] and 0xff) shl 8) or
                ((input[cPos++] and 0xff))
        }

        for (w in 16 until 64) {
            val w15 = words[w - 15]
            val s0 = ((w15 ushr 7) or (w15 shl 25)) xor ((w15 ushr 18) or (w15 shl 14)) xor (w15 ushr 3)
            val w2 = words[w - 2]
            val s1 = ((w2 ushr 17) or (w2 shl 15)) xor ((w2 ushr 19) or (w2 shl 13)) xor (w2 ushr 10)
            val w16 = words[w - 16]
            val w7 = words[w - 7]
            words[w] = w16 + s0 + w7 + s1
        }

        hash(words)
    }

    private fun hash(
        words: IntArray,
    ) {
        val localK = k
        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        var f = h5
        var g = h6
        var h = h7

        for (i in 0 until 64) {
            val s0 = ((a ushr 2) or (a shl 30)) xor
                ((a ushr 13) or (a shl 19)) xor
                ((a ushr 22) or (a shl 10))
            val s1 = ((e ushr 6) or (e shl 26)) xor
                ((e ushr 11) or (e shl 21)) xor
                ((e ushr 25) or (e shl 7))

            val ch = (e and f) xor
                (e.inv() and g)
            val maj = (a and b) xor
                (a and c) xor
                (b and c)

            val t1 = h + s1 + ch + localK[i] + words[i]
            val t2 = s0 + maj

            h = g
            g = f
            f = e
            e = d + t1
            d = c
            c = b
            b = a
            a = t1 + t2
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        h5 += f
        h6 += g
        h7 += h
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
        unprocessed[62] = (messageLengthBits ushr  8).toByte()
        unprocessed[63] = (messageLengthBits        ).toByte()
        processChunk(unprocessed, 0)

        val a = h0
        val b = h1
        val c = h2
        val d = h3
        val e = h4
        val f = h5
        val g = h6
        val h = h7

        reset()

        return byteArrayOf(
            (a shr 24).toByte(),
            (a shr 16).toByte(),
            (a shr  8).toByte(),
            (a       ).toByte(),
            (b shr 24).toByte(),
            (b shr 16).toByte(),
            (b shr  8).toByte(),
            (b       ).toByte(),
            (c shr 24).toByte(),
            (c shr 16).toByte(),
            (c shr  8).toByte(),
            (c       ).toByte(),
            (d shr 24).toByte(),
            (d shr 16).toByte(),
            (d shr  8).toByte(),
            (d       ).toByte(),
            (e shr 24).toByte(),
            (e shr 16).toByte(),
            (e shr  8).toByte(),
            (e       ).toByte(),
            (f shr 24).toByte(),
            (f shr 16).toByte(),
            (f shr  8).toByte(),
            (f       ).toByte(),
            (g shr 24).toByte(),
            (g shr 16).toByte(),
            (g shr  8).toByte(),
            (g       ).toByte(),
            (h shr 24).toByte(),
            (h shr 16).toByte(),
            (h shr  8).toByte(),
            (h       ).toByte()
        )
    }
    /* ktlint-enable */

    private fun reset() {
        messageLength = 0L
        unprocessed.fill(0)
        unprocessedLimit = 0
        words.fill(0)

        h0 = 1779033703
        h1 = -1150833019
        h2 = 1013904242
        h3 = -1521486534
        h4 = 1359893119
        h5 = -1694144372
        h6 = 528734635
        h7 = 1541459225
    }

    companion object {
        private val k = intArrayOf(
            1116352408, 1899447441, -1245643825, -373957723, 961987163, 1508970993, -1841331548,
            -1424204075, -670586216, 310598401, 607225278, 1426881987, 1925078388, -2132889090,
            -1680079193, -1046744716, -459576895, -272742522, 264347078, 604807628, 770255983, 1249150122,
            1555081692, 1996064986, -1740746414, -1473132947, -1341970488, -1084653625, -958395405,
            -710438585, 113926993, 338241895, 666307205, 773529912, 1294757372, 1396182291, 1695183700,
            1986661051, -2117940946, -1838011259, -1564481375, -1474664885, -1035236496, -949202525,
            -778901479, -694614492, -200395387, 275423344, 430227734, 506948616, 659060556, 883997877,
            958139571, 1322822218, 1537002063, 1747873779, 1955562222, 2024104815, -2067236844,
            -1933114872, -1866530822, -1538233109, -1090935817, -965641998,
        )
    }
}
