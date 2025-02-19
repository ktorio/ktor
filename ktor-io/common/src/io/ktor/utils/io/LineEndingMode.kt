/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlin.jvm.JvmInline

/**
 * Represents different line ending modes and provides operations to work with them.
 * The class uses a bitmask internally to represent different line ending combinations.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.LineEndingMode)
 */
@InternalAPI
@JvmInline
public value class LineEndingMode private constructor(private val mode: Int) {

    /**
     * Checks if this line ending mode includes another mode.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.LineEndingMode.contains)
     */
    public operator fun contains(other: LineEndingMode): Boolean =
        mode or other.mode == mode

    /**
     * Combines this line ending mode with another mode.
     * The resulting mode will accept both line endings.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.LineEndingMode.plus)
     */
    public operator fun plus(other: LineEndingMode): LineEndingMode =
        LineEndingMode(mode or other.mode)

    override fun toString(): String = when (this) {
        CR -> "CR"
        LF -> "LF"
        CRLF -> "CRLF"
        else -> values.filter { it in this }.toString()
    }

    public companion object {
        /**
         * Represents Carriage Return (\r) line ending.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.LineEndingMode.CR)
         */
        public val CR: LineEndingMode = LineEndingMode(0b001)

        /**
         * Represents Line Feed (\n) line ending.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.LineEndingMode.LF)
         */
        public val LF: LineEndingMode = LineEndingMode(0b010)

        /**
         * Represents Carriage Return + Line Feed (\r\n) line ending.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.LineEndingMode.CRLF)
         */
        public val CRLF: LineEndingMode = LineEndingMode(0b100)

        /**
         * Represents a mode that accepts any line ending ([CR], [LF], or [CRLF]).
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.LineEndingMode.Any)
         */
        public val Any: LineEndingMode = LineEndingMode(0b111)

        private val values = listOf(CR, LF, CRLF)
    }
}
