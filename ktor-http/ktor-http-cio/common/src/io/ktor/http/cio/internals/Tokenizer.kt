/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio.internals

internal fun nextToken(text: CharSequence, range: MutableRange): CharSequence {
    val spaceOrEnd = findSpaceOrEnd(text, range)
    val s = text.subSequence(range.start, spaceOrEnd)
    range.start = spaceOrEnd
    return s
}

internal fun skipSpacesAndHorizontalTabs(
    text: CharArrayBuilder,
    start: Int,
    end: Int
): Int {
    var index = start
    while (index < end) {
        val ch = text[index]
        if (ch != ' ' && ch != HTAB) break
        index++
    }
    return index
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
