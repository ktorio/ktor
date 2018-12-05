package io.ktor.http.cio.internals

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
