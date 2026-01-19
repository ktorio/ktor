/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

/**
 * Represents different line ending modes supported by Ktor.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.LineEnding)
 */
public enum class LineEnding {
    /**
     * Recognizes LF (\n) and CRLF (\r\n) as line delimiters.
     *
     * This is the default and recommended for most use cases.
     */
    Default,

    /**
     * Recognizes LF (\n), CRLF (\r\n), and CR (\r) as line delimiters.
     *
     * It is recommended to use [Default] when possible as it is usually more efficient.
     */
    Lenient,
}
