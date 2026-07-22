/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing

import io.ktor.http.*
import kotlin.jvm.JvmInline

private const val DELIMITER = '/'

/**
 * A zero-allocation view of a request path as an ordered list of URL-decoded segments.
 *
 * `SegmentedPath` is an inline value class wrapping a single `String`: the raw, still-encoded
 * request path *without a leading slash*. Iteration and indexed access produce decoded
 * segments lazily (via [String.decodeURLPart]), so the routing fast-path can match constant
 * segments using cheap region comparisons against the wrapped string and only pay the
 * per-segment substring/decode cost when a slow-path selector actually inspects the value.
 *
 * Segment splitting:
 *  - Empty segments produced by consecutive `'/'` characters are skipped, matching the
 *    pre-existing parser behavior.
 *  - A trailing empty segment is emitted when the wrapped string ends with `'/'`. Callers
 *    that want trailing-slash-insensitive routing must strip the trailing slash before
 *    constructing a `SegmentedPath`.
 *
 * Decoding:
 *  - URL decoding is applied per-segment, not over the whole path. This preserves the
 *    semantics of an encoded `'/'` inside a segment (e.g. `%2F`), which must not be treated
 *    as a path separator.
 *
 * This class deliberately implements [List]<[String]> to be a drop-in replacement for the
 * previous `List<String>` parse result while remaining allocation-free at construction.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.SegmentedPath)
 */
@JvmInline
internal value class SegmentedPath internal constructor(
    private val string: String
) : List<String> {

    /**
     * Iterates each segment's raw `[start, end)` range in [string], invoking [action]
     * with the segment index and range. Empty segments are skipped, with the sole exception
     * of a trailing empty segment when [string] ends with `'/'`.
     */
    private inline fun forEachSegmentRange(action: (index: Int, start: Int, end: Int) -> Unit) {
        val len = string.length
        if (len == 0) return
        var i = 0
        var count = 0
        while (i < len) {
            val next = string.indexOf(DELIMITER, i)
            val end = if (next == -1) len else next
            if (end != i) {
                action(count++, i, end)
            }
            if (next == -1) return
            i = next + 1
        }
        // String ended with '/': emit a trailing empty segment.
        action(count, len, len)
    }

    private inline fun forEachSegment(action: (Int, String) -> Unit) {
        forEachSegmentRange { index, start, end ->
            action(index, decodeSegment(start, end))
        }
    }

    private fun decodeSegment(start: Int, end: Int): String =
        if (start == end) "" else string.decodeURLPart(start, end)

    override val size: Int
        get() {
            var count = 0
            forEachSegmentRange { _, _, _ -> count++ }
            return count
        }

    override fun isEmpty(): Boolean = string.isEmpty()

    override fun get(index: Int): String {
        if (index < 0) throw IndexOutOfBoundsException("Index $index is out of bounds")
        forEachSegmentRange { i, start, end ->
            if (i == index) return decodeSegment(start, end)
        }
        throw IndexOutOfBoundsException("Index $index is out of bounds")
    }

    override fun contains(element: String): Boolean = indexOf(element) >= 0

    override fun containsAll(elements: Collection<String>): Boolean = elements.all { contains(it) }

    override fun indexOf(element: String): Int {
        forEachSegment { i, segment ->
            if (segment == element) return i
        }
        return -1
    }

    override fun lastIndexOf(element: String): Int {
        var result = -1
        forEachSegment { i, segment ->
            if (segment == element) result = i
        }
        return result
    }

    override fun iterator(): Iterator<String> = iterator {
        forEachSegment { _, string ->
            yield(string)
        }
    }

    override fun listIterator(): ListIterator<String> = listIterator(0)

    override fun listIterator(index: Int): ListIterator<String> {
        val list = ArrayList<String>()
        forEachSegment { _, segment -> list.add(segment) }
        return list.listIterator(index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<String> {
        require(fromIndex in 0..toIndex) { "fromIndex=$fromIndex, toIndex=$toIndex" }
        val result = ArrayList<String>()
        forEachSegment { i, string ->
            if (i >= toIndex) return result
            if (i >= fromIndex) result.add(string)
        }
        return result
    }

    /**
     * Produces a [List]<[String]>-style representation so that diagnostics and trace output
     * (which historically saw a `List<String>` here) continue to render identically.
     */
    override fun toString(): String {
        val sb = StringBuilder().append('[')
        forEachSegment { i, segment ->
            if (i > 0) sb.append(", ")
            sb.append(segment)
        }
        sb.append(']')
        return sb.toString()
    }
}
