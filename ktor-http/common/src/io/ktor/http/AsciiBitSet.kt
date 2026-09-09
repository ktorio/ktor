/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

/**
 * An immutable membership table for a set of ASCII characters, backed by two 64-bit masks.
 *
 * Membership is two shifts and a bitwise AND — no boxing and no hashing, unlike `Set<Char>`,
 * which boxes every probed character. Code points outside `0..127` are never members.
 */
internal class AsciiBitSet private constructor(private val low: Long, private val high: Long) {

    operator fun contains(char: Char): Boolean = containsCode(char.code)

    operator fun contains(byte: Byte): Boolean = containsCode(byte.toInt())

    operator fun plus(other: AsciiBitSet): AsciiBitSet = AsciiBitSet(low or other.low, high or other.high)

    private fun containsCode(code: Int): Boolean {
        if (code ushr 7 != 0) return false
        val bits = if (code < 64) low else high
        // Long shifts use the lowest 6 bits of the distance, so `code` addresses both halves
        return (bits ushr code) and 1L == 1L
    }

    companion object {
        fun of(chars: Iterable<Char>): AsciiBitSet {
            var low = 0L
            var high = 0L
            for (char in chars) {
                val code = char.code
                require(code < 128) { "Only ASCII characters are supported, got '$char' (code $code)" }
                if (code < 64) low = low or (1L shl code) else high = high or (1L shl code)
            }
            return AsciiBitSet(low, high)
        }

        fun of(chars: String): AsciiBitSet = of(chars.asIterable())
    }
}
