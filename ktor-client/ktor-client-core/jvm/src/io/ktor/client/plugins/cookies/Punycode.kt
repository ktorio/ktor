/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors.
 * Copyright (C) 2022 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.ktor.client.plugins.cookies

/**
 * An RFC 3492 Punycode encoder and decoder adapted from OkHttp 5.5.0 without its Okio dependency.
 *
 * Source: https://github.com/square/okhttp/blob/parent-5.5.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/idn/Punycode.kt
 */
internal object Punycode {
    const val PREFIX: String = "xn--"

    private const val BASE = 36
    private const val T_MIN = 1
    private const val T_MAX = 26
    private const val SKEW = 38
    private const val DAMP = 700
    private const val INITIAL_BIAS = 72
    private const val INITIAL_N = 0x80

    /** Returns `null` if a label is malformed or cannot be encoded without integer overflow. */
    fun encode(string: String): String? {
        var position = 0
        val result = StringBuilder()

        while (position < string.length) {
            val dot = string.indexOf('.', startIndex = position).takeIf { it >= 0 } ?: string.length
            if (!encodeLabel(string, position, dot, result)) return null

            if (dot == string.length) break
            result.append('.')
            position = dot + 1
        }

        return result.toString()
    }

    private fun encodeLabel(
        string: String,
        position: Int,
        limit: Int,
        result: StringBuilder
    ): Boolean {
        if (!string.requiresEncoding(position, limit)) {
            result.append(string, position, limit)
            return true
        }

        val input = string.codePoints(position, limit) ?: return false
        result.append(PREFIX)

        var basicCount = 0
        for (codePoint in input) {
            if (codePoint < INITIAL_N) {
                result.append(codePoint.toChar())
                basicCount++
            }
        }

        if (basicCount > 0) result.append('-')

        var n = INITIAL_N
        var delta = 0
        var bias = INITIAL_BIAS
        var handled = basicCount
        while (handled < input.size) {
            val next = input.minOf { if (it >= n) it else Int.MAX_VALUE }
            val increment = (next - n).toLong() * (handled + 1)
            if (increment > Int.MAX_VALUE - delta) return false
            delta += increment.toInt()
            n = next

            for (codePoint in input) {
                if (codePoint < n) {
                    if (delta == Int.MAX_VALUE) return false
                    delta++
                } else if (codePoint == n) {
                    var value = delta

                    for (k in BASE until Int.MAX_VALUE step BASE) {
                        val threshold = threshold(k, bias)
                        if (value < threshold) break
                        result.append(encodeDigit(threshold + (value - threshold) % (BASE - threshold)))
                        value = (value - threshold) / (BASE - threshold)
                    }

                    result.append(encodeDigit(value))
                    bias = adapt(delta, handled + 1, handled == basicCount)
                    delta = 0
                    handled++
                }
            }

            if (delta == Int.MAX_VALUE || n == Int.MAX_VALUE) return false
            delta++
            n++
        }

        return true
    }

    /** Returns `null` if a Punycode label is malformed or cannot be decoded without integer overflow. */
    fun decode(string: String): String? {
        var position = 0
        val result = StringBuilder()

        while (position < string.length) {
            val dot = string.indexOf('.', startIndex = position).takeIf { it >= 0 } ?: string.length
            if (!decodeLabel(string, position, dot, result)) return null

            if (dot == string.length) break
            result.append('.')
            position = dot + 1
        }

        return result.toString()
    }

    private fun decodeLabel(
        string: String,
        position: Int,
        limit: Int,
        result: StringBuilder
    ): Boolean {
        if (!string.regionMatches(position, PREFIX, 0, PREFIX.length, ignoreCase = true)) {
            result.append(string, position, limit)
            return true
        }

        var current = position + PREFIX.length
        val codePoints = mutableListOf<Int>()
        val lastDelimiter = string.lastIndexOf('-', startIndex = limit - 1)
        if (lastDelimiter >= current) {
            while (current < lastDelimiter) {
                val codePoint = string[current++]
                if (codePoint !in 'a'..'z' && codePoint !in 'A'..'Z' && codePoint !in '0'..'9' && codePoint != '-') {
                    return false
                }
                codePoints += codePoint.code
            }
            current++
        }

        var n = INITIAL_N
        var index = 0
        var bias = INITIAL_BIAS

        while (current < limit) {
            val oldIndex = index
            var weight = 1

            for (k in BASE until Int.MAX_VALUE step BASE) {
                if (current == limit) return false
                val digit = decodeDigit(string[current++]) ?: return false
                if (digit != 0 && weight > Int.MAX_VALUE / digit) return false
                val increment = digit * weight
                if (index > Int.MAX_VALUE - increment) return false
                index += increment

                val threshold = threshold(k, bias)
                if (digit < threshold) break
                val scale = BASE - threshold
                if (weight > Int.MAX_VALUE / scale) return false
                weight *= scale
            }

            bias = adapt(index - oldIndex, codePoints.size + 1, oldIndex == 0)
            val increment = index / (codePoints.size + 1)
            if (n > Int.MAX_VALUE - increment) return false
            n += increment
            index %= codePoints.size + 1

            if (n > 0x10ffff || n in 0xd800..0xdfff) return false
            codePoints.add(index, n)
            index++
        }

        for (codePoint in codePoints) {
            result.appendCodePoint(codePoint)
        }
        return true
    }

    private fun adapt(sourceDelta: Int, points: Int, firstTime: Boolean): Int {
        var delta = if (firstTime) sourceDelta / DAMP else sourceDelta / 2
        delta += delta / points
        var result = 0
        while (delta > ((BASE - T_MIN) * T_MAX) / 2) {
            delta /= BASE - T_MIN
            result += BASE
        }
        return result + (((BASE - T_MIN + 1) * delta) / (delta + SKEW))
    }

    private fun threshold(k: Int, bias: Int): Int = when {
        k <= bias -> T_MIN
        k >= bias + T_MAX -> T_MAX
        else -> k - bias
    }

    private fun encodeDigit(value: Int): Char = when {
        value < 26 -> ('a'.code + value).toChar()
        value < 36 -> ('0'.code + value - 26).toChar()
        else -> error("Unexpected Punycode digit: $value")
    }

    private fun decodeDigit(value: Char): Int? = when (value) {
        in 'a'..'z' -> value - 'a'
        in 'A'..'Z' -> value - 'A'
        in '0'..'9' -> value - '0' + 26
        else -> null
    }

    private fun String.requiresEncoding(position: Int, limit: Int): Boolean {
        for (index in position until limit) {
            if (this[index].code >= INITIAL_N) return true
        }
        return false
    }

    private fun String.codePoints(position: Int, limit: Int): List<Int>? {
        val result = mutableListOf<Int>()
        var index = position
        while (index < limit) {
            val high = this[index]
            when {
                high.isHighSurrogate() -> {
                    val low = getOrNull(index + 1)?.takeIf { index + 1 < limit } ?: return null
                    if (!low.isLowSurrogate()) return null
                    result += Character.toCodePoint(high, low)
                    index += 2
                }

                high.isLowSurrogate() -> return null

                else -> {
                    result += high.code
                    index++
                }
            }
        }
        return result
    }
}
