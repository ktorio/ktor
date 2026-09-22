/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*
import io.ktor.utils.io.*

/**
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.expectHttpUpgrade)
 *
 * @return `true` if an http upgrade is expected according to request [method], [upgrade] header value and
 * parsed [connectionOptions]
 */
public fun expectHttpUpgrade(
    method: HttpMethod,
    upgrade: CharSequence?,
    connectionOptions: ConnectionOptions?
): Boolean = method == HttpMethod.Get &&
    upgrade != null &&
    connectionOptions?.upgrade == true

/**
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.expectHttpUpgrade)
 *
 * @return `true` if an http upgrade is expected according to [request]
 */
public fun expectHttpUpgrade(request: Request): Boolean = expectHttpUpgrade(
    request.method,
    request.headers["Upgrade"],
    ConnectionOptions.parse(request.headers["Connection"])
)

/**
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.expectHttpBody)
 *
 * @return `true` if request or response with the specified parameters could have a body
 * @throws ParserException if [transferEncoding] is present and is not exactly `chunked`
 */
public fun expectHttpBody(
    method: HttpMethod,
    contentLength: Long,
    transferEncoding: CharSequence?,
    connectionOptions: ConnectionOptions?,
    @Suppress("UNUSED_PARAMETER") contentType: CharSequence?
): Boolean {
    if (transferEncoding != null) {
        requireChunkedTransferEncoding(transferEncoding)
        return true
    }
    if (contentLength != -1L) return contentLength > 0L

    if (method == HttpMethod.Get || method == HttpMethod.Head || method == HttpMethod.Options) return false
    if (connectionOptions?.close == true) return true

    return false
}

/**
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.expectHttpBody)
 *
 * @return `true` if request or response with the specified parameters could have a body
 * @throws ParserException if the message has more than one `Content-Length` or `Transfer-Encoding` header,
 * has both of them, or has a `Transfer-Encoding` other than `chunked`
 */
public fun expectHttpBody(request: Request): Boolean {
    val contentLength = request.headers.singleOrNull("Content-Length")
    val transferEncoding = request.headers.singleOrNull("Transfer-Encoding")
    val connectionOptions = ConnectionOptions.parse(request.headers["Connection"])
    val contentType = request.headers["Content-Type"]
    checkFramingHeaders(contentLength, transferEncoding)
    return expectHttpBody(
        request.method,
        contentLength?.parseDecLong() ?: -1,
        transferEncoding,
        connectionOptions,
        contentType
    )
}

/**
 * Parse HTTP request or response body using [contentLength], [transferEncoding] and [connectionOptions]
 * writing it to [out].
 * Usually doesn't fail but closing [out] channel with error.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.parseHttpBody)
 *
 * @param contentLength from the corresponding header or -1
 * @param transferEncoding header or `null`
 * @param
 */
public suspend fun parseHttpBody(
    version: HttpProtocolVersion?,
    contentLength: Long,
    transferEncoding: CharSequence?,
    connectionOptions: ConnectionOptions?,
    input: ByteReadChannel,
    out: ByteWriteChannel
) {
    if (transferEncoding != null && isTransferEncodingChunked(transferEncoding)) {
        return decodeChunked(input, out)
    }

    if (contentLength != -1L) {
        input.copyTo(out, contentLength)
        return
    }

    if (connectionOptions?.close == true || (connectionOptions == null && version == HttpProtocolVersion.HTTP_1_0)) {
        input.copyTo(out, Long.MAX_VALUE)
        return
    }

    val cause = IllegalStateException(
        """
            Failed to parse request body: request body length should be specified,
            chunked transfer encoding should be used or
            keep-alive should be disabled (connection: close)
        """.trimIndent()
    )

    out.close(cause)
}

/**
 * Parse HTTP request or response body using [contentLength], [transferEncoding] and [connectionOptions]
 * writing it to [out].
 * Usually doesn't fail but closing [out] channel with error.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.parseHttpBody)
 *
 * @param contentLength from the corresponding header or -1
 * @param transferEncoding header or `null`
 * @param
 */
@Deprecated(
    message = "Please use method with version parameter",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("parseHttpBody(null, contentLength, transferEncoding, connectionOptions, input, out)")
)
public suspend fun parseHttpBody(
    contentLength: Long,
    transferEncoding: CharSequence?,
    connectionOptions: ConnectionOptions?,
    input: ByteReadChannel,
    out: ByteWriteChannel
) {
    parseHttpBody(null, contentLength, transferEncoding, connectionOptions, input, out)
}

/**
 * Parse HTTP request or response body using request/response's [headers]
 * writing it to [out]. Usually doesn't fail but closing [out] channel with error.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.parseHttpBody)
 *
 * @throws ParserException if the message has more than one `Content-Length`
 * or `Transfer-Encoding` header, has both of them, or has a `Transfer-Encoding` other than `chunked`
 */
public suspend fun parseHttpBody(
    headers: HttpHeadersMap,
    input: ByteReadChannel,
    out: ByteWriteChannel
) {
    val contentLength = headers.singleOrNull("Content-Length")
    val transferEncoding = headers.singleOrNull("Transfer-Encoding")
    val connectionOptions = ConnectionOptions.parse(headers["Connection"])

    checkFramingHeaders(contentLength, transferEncoding)
    transferEncoding?.let(::requireChunkedTransferEncoding)

    parseHttpBody(
        version = null,
        contentLength?.parseDecLong() ?: -1,
        transferEncoding,
        connectionOptions,
        input,
        out
    )
}

/**
 * Returns the single value of the [name] header or `null` if absent.
 *
 * @throws ParserException if the header appears more than once (RFC 9110, Section 5.3)
 */
internal fun HttpHeadersMap.singleOrNull(name: String): CharSequence? {
    val iterator = getAll(name).iterator()
    if (!iterator.hasNext()) return null
    val value = iterator.next()
    if (iterator.hasNext()) throw ParserException("Duplicate $name header")
    return value
}

/**
 * A message must not carry both framing headers, otherwise its body length is ambiguous
 * and the message could be used for request smuggling (RFC 9112, Section 6.1).
 */
internal fun checkFramingHeaders(contentLength: CharSequence?, transferEncoding: CharSequence?) {
    if (contentLength != null && transferEncoding != null) {
        throw ParserException("Transfer-Encoding and Content-Length headers must not be sent together")
    }
}

internal fun requireChunkedTransferEncoding(transferEncoding: CharSequence) {
    // Only SP and HTAB are optional whitespace in header values
    val trimmed = transferEncoding.trim { it == ' ' || it == '\t' }
    if (!trimmed.equalsLowerCase(other = "chunked")) {
        throw ParserException("Unsupported Transfer-Encoding: '$transferEncoding'. Only 'chunked' is supported")
    }
}

private fun isTransferEncodingChunked(transferEncoding: CharSequence): Boolean {
    if (transferEncoding.equalsLowerCase(other = "chunked")) {
        return true
    }
    if (transferEncoding.equalsLowerCase(other = "identity")) {
        return false
    }

    var chunked = false
    transferEncoding.split(",").forEach {
        when (val name = it.trim().lowercase()) {
            "chunked" -> {
                if (chunked) {
                    throw IllegalArgumentException("Double-chunked TE is not supported: $transferEncoding")
                }
                chunked = true
            }

            "identity" -> {
                // ignore this token
            }

            else -> throw IllegalArgumentException("Unsupported transfer encoding $name")
        }
    }

    return chunked
}
