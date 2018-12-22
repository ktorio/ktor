package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*
import io.ktor.util.*
import kotlinx.coroutines.io.*

/**
 * @return `true` if an http upgrade is expected accoding to request [method], [upgrade] header value and
 * parsed [connectionOptions]
 */
@KtorExperimentalAPI
fun expectHttpUpgrade(
    method: HttpMethod,
    upgrade: CharSequence?,
    connectionOptions: ConnectionOptions?
): Boolean = method == HttpMethod.Get &&
    upgrade != null &&
    connectionOptions?.upgrade == true

/**
 * @return `true` if an http upgrade is expected according to [request]
 */
@KtorExperimentalAPI
fun expectHttpUpgrade(request: Request): Boolean = expectHttpUpgrade(
    request.method,
    request.headers["Upgrade"],
    ConnectionOptions.parse(request.headers["Connection"])
)

/**
 * @return `true` if request or response with the specified parameters could have a body
 */
@KtorExperimentalAPI
fun expectHttpBody(
    method: HttpMethod,
    contentLength: Long,
    transferEncoding: CharSequence?,
    connectionOptions: ConnectionOptions?,
    contentType: CharSequence?
): Boolean {
    if (method == HttpMethod.Get || method == HttpMethod.Head || method == HttpMethod.Options) return false

    if (transferEncoding != null || connectionOptions?.close == true) return true
    if (contentLength != -1L) return contentLength > 0L
    if (contentType != null) return true

    return false
}

/**
 * @return `true` if request or response with the specified parameters could have a body
 */
@KtorExperimentalAPI
fun expectHttpBody(request: Request): Boolean = expectHttpBody(
    request.method,
    request.headers["Content-Length"]?.parseDecLong() ?: -1,
    request.headers["Transfer-Encoding"],
    ConnectionOptions.parse(request.headers["Connection"]),
    request.headers["Content-Type"]
)

/**
 * Parse HTTP request or response body using [contentLength], [transferEncoding] and [connectionOptions]
 * writing it to [out]. Usually doesn't fail but closing [out] channel with error.
 */
@KtorExperimentalAPI
suspend fun parseHttpBody(
    contentLength: Long,
    transferEncoding: CharSequence?,
    connectionOptions: ConnectionOptions?,
    input: ByteReadChannel,
    out: ByteWriteChannel
) {
    if (transferEncoding != null) {
        when {
            transferEncoding.equalsLowerCase(other = "chunked") -> return decodeChunked(input, out)
            transferEncoding.equalsLowerCase(other = "identity") -> {
                // do nothing special
            }
            else -> out.close(IllegalStateException("Unsupported transfer-encoding $transferEncoding"))
            // TODO: combined transfer encodings
        }
    }

    if (contentLength != -1L) {
        input.copyTo(out, contentLength)
        return
    }

    if (connectionOptions?.close == true) {
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
 * Parse HTTP request or response body using request/response's [headers]
 * writing it to [out]. Usually doesn't fail but closing [out] channel with error.
 */
@KtorExperimentalAPI
suspend fun parseHttpBody(
    headers: HttpHeadersMap,
    input: ByteReadChannel,
    out: ByteWriteChannel
): Unit = parseHttpBody(
    headers["Content-Length"]?.parseDecLong() ?: -1,
    headers["Transfer-Encoding"],
    ConnectionOptions.parse(headers["Connection"]),
    input, out
)
