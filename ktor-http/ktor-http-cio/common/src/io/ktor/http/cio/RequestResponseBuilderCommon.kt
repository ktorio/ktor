/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.utils.io.core.*


/**
 * Builds an HTTP request or response
 */
expect class RequestResponseBuilder() {
    /**
     * Append response status line
     */
    fun responseLine(version: CharSequence, status: Int, statusText: CharSequence)

    /**
     * Append request line
     */
    fun requestLine(method: HttpMethod, uri: CharSequence, version: CharSequence)

    /**
     * Append a line
     */
    fun line(line: CharSequence)

    /**
     * Append raw bytes
     */
    fun bytes(content: ByteArray, offset: Int = 0, length: Int = content.size)

    /**
     * Append header line
     */
    fun headerLine(name: CharSequence, value: CharSequence)

    /**
     * Append an empty line (CR + LF in fact)
     */
    fun emptyLine()

    /**
     * Build a packet of request/response
     */
    fun build(): ByteReadPacket

    /**
     * Release all resources hold by the builder
     */
    fun release()
}

private const val SP: Byte = 0x20
private const val CR: Byte = 0x0d
private const val LF: Byte = 0x0a
