package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*
import kotlinx.io.core.*

/**
 * Represents a base HTTP message type for request and response
 * @property headers request/response headers
 */
abstract class HttpMessage internal constructor(
    val headers: HttpHeadersMap, private val builder: CharArrayBuilder
) : Closeable {

    /**
     * Release all memory resources hold by this message
     */
    fun release() {
        builder.release()
        headers.release()
    }

    /**
     * Release all memory resources hold by this message
     */
    override fun close() {
        release()
    }
}

/**
 * Represents an HTTP request
 * @property method
 * @property uri
 * @property version
 */
class Request internal constructor(
    val method: HttpMethod,
    val uri: CharSequence,
    val version: CharSequence,
    headers: HttpHeadersMap,
    builder: CharArrayBuilder
) : HttpMessage(headers, builder)

/**
 * Represents an HTTP response
 * @property version
 * @property status
 * @property statusText
 */
class Response internal constructor(
    val version: CharSequence,
    val status: Int,
    val statusText: CharSequence,
    headers: HttpHeadersMap,
    builder: CharArrayBuilder
) : HttpMessage(headers, builder)
