package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*

abstract class HttpMessage internal constructor(val headers: HttpHeadersMap, private val builder: CharBufferBuilder) {
    fun release() {
        builder.release()
        headers.release()
    }
}

class Request internal constructor(
    val method: HttpMethod,
    val uri: CharSequence,
    val version: CharSequence,
    headers: HttpHeadersMap,
    builder: CharBufferBuilder
) : HttpMessage(headers, builder)

class Response internal constructor(
    val version: CharSequence,
    val status: Int,
    val statusText: CharSequence,
    headers: HttpHeadersMap,
    builder: CharBufferBuilder
) : HttpMessage(headers, builder)
