package io.ktor.http.cio.internals

import io.ktor.http.cio.*

internal fun nextToken(text: CharSequence, range: MutableRange): CharSequence {
    val spaceOrEnd = findSpaceOrEnd(text, range)
    val s = text.subSequence(range.start, spaceOrEnd)
    range.start = spaceOrEnd
    return s
}

internal fun skipSpaces(text: CharSequence, range: MutableRange) {
    var idx = range.start
    val end = range.end

    if (idx >= end || text[idx] != ' ') return
    idx++

    while (idx < end) {
        if (text[idx] != ' ') break
        idx++
    }

    range.start = idx
}

internal fun skipSpacesAndColon(text: CharSequence, range: MutableRange) {
    var idx = range.start
    val end = range.end
    var colons = 0

    while (idx < end) {
        val ch = text[idx]
        if (ch == ':') {
            if (++colons > 1) {
                throw ParserException("Multiple colons in header")
            }
        } else if (ch != ' ') {
            break
        }

        idx++
    }

    range.start = idx
}

internal fun findSpaceOrEnd(text: CharSequence, range: MutableRange): Int {
    var idx = range.start
    val end = range.end

    if (idx >= end || text[idx] == ' ') return idx
    idx++

    while (idx < end) {
        if (text[idx] == ' ') return idx
        idx++
    }

    return idx
}

internal fun findColonOrSpace(text: CharSequence, range: MutableRange): Int {
    var idx = range.start
    val end = range.end

    while (idx < end) {
        val ch = text[idx]
        if (ch == ' ' || ch == ':') return idx
        idx++
    }

    return idx
}
