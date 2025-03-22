/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*
import io.ktor.utils.io.core.*

/**
 * Represents a base HTTP message type for request and response
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.HttpMessage)
 *
 * @property headers request/response headers
 */
public abstract class HttpMessage internal constructor(
    public val headers: HttpHeadersMap,
    private val builder: CharArrayBuilder
) : Closeable {

    /**
     * Release all memory resources hold by this message
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.HttpMessage.release)
     */
    public fun release() {
        builder.release()
        headers.release()
    }

    /**
     * Release all memory resources hold by this message
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.HttpMessage.close)
     */
    override fun close() {
        release()
    }
}

/**
 * Represents an HTTP request
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.Request)
 *
 * @property method
 * @property uri
 * @property version
 */
public class Request internal constructor(
    public val method: HttpMethod,
    public val uri: CharSequence,
    public val version: CharSequence,
    headers: HttpHeadersMap,
    builder: CharArrayBuilder
) : HttpMessage(headers, builder)

/**
 * Represents an HTTP response
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.Response)
 *
 * @property version
 * @property status
 * @property statusText
 */
public class Response internal constructor(
    public val version: CharSequence,
    public val status: Int,
    public val statusText: CharSequence,
    headers: HttpHeadersMap,
    builder: CharArrayBuilder
) : HttpMessage(headers, builder)
