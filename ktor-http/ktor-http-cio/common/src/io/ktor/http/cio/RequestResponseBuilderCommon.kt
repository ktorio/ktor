/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio

import io.ktor.http.*
import kotlinx.io.*

/**
 * Builds an HTTP request or response
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.RequestResponseBuilder)
 */
public expect class RequestResponseBuilder() {
    /**
     * Append response status line
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.RequestResponseBuilder.responseLine)
     */
    public fun responseLine(version: CharSequence, status: Int, statusText: CharSequence)

    /**
     * Append request line
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.RequestResponseBuilder.requestLine)
     */
    public fun requestLine(method: HttpMethod, uri: CharSequence, version: CharSequence)

    /**
     * Append a line
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.RequestResponseBuilder.line)
     */
    public fun line(line: CharSequence)

    /**
     * Append raw bytes
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.RequestResponseBuilder.bytes)
     */
    public fun bytes(content: ByteArray, offset: Int = 0, length: Int = content.size)

    /**
     * Append header line
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.RequestResponseBuilder.headerLine)
     */
    public fun headerLine(name: CharSequence, value: CharSequence)

    /**
     * Append an empty line (CR + LF in fact)
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.RequestResponseBuilder.emptyLine)
     */
    public fun emptyLine()

    /**
     * Build a packet of request/response
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.RequestResponseBuilder.build)
     */

    public fun build(): Source

    /**
     * Release all resources hold by the builder
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.RequestResponseBuilder.release)
     */
    public fun release()
}
