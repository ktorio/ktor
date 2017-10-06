package io.ktor.http.cio.internals

import io.ktor.http.*

internal fun CharSequence.hashCodeLowerCase(start: Int = 0, end: Int = length): Int {
    var hashCode = 0
    for (pos in start until end) {
        val v = get(pos).toInt().toLowerCase()
        hashCode = 31 * hashCode + v
    }

    return hashCode
}

internal fun CharSequence.equalsLowerCase(start: Int = 0, end: Int = length, other: CharSequence): Boolean {
    if  (end - start != other.length) return false

    for (pos in start until end) {
        if (get(pos).toInt().toLowerCase() != other.get(pos - start).toInt().toLowerCase()) return false
    }

    return true
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Int.toLowerCase() = if (this in 'A'.toInt() .. 'Z'.toInt()) 'a'.toInt() + (this - 'A'.toInt()) else this

internal val DefaultHttpMethods =
    AsciiCharTree.build(HttpMethod.DefaultMethods, { it.value.length }, { m, idx -> m.value[idx] })


private val HexTable = (0..0xff).map { v ->
    when {
        v in 0x30..0x39 -> v - 0x30L
        v >= 'a'.toLong() && v <= 'f'.toLong() -> v - 'a'.toLong() + 10
        v >= 'A'.toLong() && v <= 'F'.toLong() -> v - 'A'.toLong() + 10
        else -> -1L
    }
}.toTypedArray()

internal fun CharSequence.parseHexLong(): Long {
    var result = 0L
    val table = HexTable
    for (i in 0 until length) {
        val v = this[i].toInt() and 0xffff
        val digit = if (v < 0xff) table[v] else -1L
        if (digit == -1L) throw NumberFormatException("Invalid HEX number: $this, wrong digit: ${this[i]}")

        result = (result shl 4) or digit
    }

    return result
}

internal fun CharSequence.parseDecLong(): Long {
    var result = 0L
    for (i in 0 until length) {
        val v = this[i].toInt() and 0xffff
        val digit = if (v in 0x30..0x39) v.toLong() - 0x30 else -1L
        if (digit == -1L) throw NumberFormatException("Invalid number: $this, wrong digit: ${this[i]}")

        result = (result * 10) or digit
    }

    return result
}

