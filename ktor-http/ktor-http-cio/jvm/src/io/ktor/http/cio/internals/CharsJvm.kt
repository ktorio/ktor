/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio.internals

import io.ktor.utils.io.*
import java.nio.*

internal fun ByteBuffer.writeIntHex(value: Int): Int {
    require(value > 0) { "Does only work for positive numbers" } // zero is not included!
    var current = value
    var zeroes = 0
    val table = HexLetterTable

    repeat(8) { idx ->
        val v = current and 0x0f
        if (v == 0) zeroes++ else zeroes = 0
        current = current ushr 4

        put(7 - idx, table[v])
    }

    return zeroes
}

internal fun Int.toHex(value: Int): ByteArray {
    require(value > 0) { "Does only work for positive numbers" } // zero is not included!
    var current = value
    var zeroes = 0
    val table = HexLetterTable
    val result = ByteArray(8)

    repeat(8) { idx ->
        val v = current and 0x0f
        if (v == 0) zeroes++ else zeroes = 0
        current = current ushr 4

        result[7 - idx] = table[v]
    }

    return result
}
