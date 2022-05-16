/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core.internal

@Suppress("RedundantModalityModifier")
internal class CharArraySequence(
    private val array: CharArray,
    private val offset: Int,
    final override val length: Int
) : CharSequence {
    final override fun get(index: Int): Char {
        if (index >= length) {
            indexOutOfBounds(index)
        }
        return array[index + offset]
    }

    final override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        require(startIndex >= 0) { "startIndex shouldn't be negative: $startIndex" }
        require(startIndex <= length) { "startIndex is too large: $startIndex > $length" }
        require(startIndex + endIndex <= length) { "endIndex is too large: $endIndex > $length" }
        require(endIndex >= startIndex) { "endIndex should be greater or equal to startIndex: $startIndex > $endIndex" }

        return CharArraySequence(array, offset + startIndex, endIndex - startIndex)
    }

    private fun indexOutOfBounds(index: Int): Nothing {
        throw IndexOutOfBoundsException("String index out of bounds: $index > $length")
    }
}
