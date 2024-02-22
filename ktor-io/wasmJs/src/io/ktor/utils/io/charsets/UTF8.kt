/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import io.ktor.utils.io.js.JsTextEncoder

@Suppress("DEPRECATION")
internal fun encodeUTF8(input: CharSequence, fromIndex: Int, toIndex: Int, dst: Buffer): Int {
    // Only UTF-8 is supported so we know that at most 6 bytes per character is used
    val encoder = JsTextEncoder.create()
    var start = fromIndex
    var dstRemaining = dst.writeRemaining

    while (start < toIndex && dstRemaining > 0) {
        val numChars = minOf(toIndex - start, dstRemaining / 6).coerceAtLeast(1)
        val dropLastChar = input[start + numChars - 1].isHighSurrogate()
        val endIndexExclusive = when {
            dropLastChar && numChars == 1 -> start + 2
            dropLastChar -> start + numChars - 1
            else -> start + numChars
        }

        val array1 = encoder.encode(input.substring(start, endIndexExclusive))
        if (array1.size > dstRemaining) break
        dst.writeFully(array1)
        start = endIndexExclusive
        dstRemaining -= array1.size
    }

    return start - fromIndex
}
