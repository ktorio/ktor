package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*
import kotlinx.coroutines.experimental.io.*
import java.io.*

fun expectHttpUpgrade(method: HttpMethod,
                      upgrade: CharSequence?,
                      connectionType: ConnectionType?): Boolean {
    return method == HttpMethod.Get &&
            upgrade != null &&
            connectionType == ConnectionType.Upgrade
}

fun expectHttpUpgrade(request: Request): Boolean {
    return expectHttpUpgrade(request.method,
            request.headers["Upgrade"],
            connectionType(request.headers["Connection"]))
}

fun expectHttpBody(method: HttpMethod,
                   contentLength: Long,
                   transferEncoding: CharSequence?,
                   connectionType: ConnectionType?,
                   contentType: CharSequence?): Boolean {
    if (method == HttpMethod.Get ||
            method == HttpMethod.Head ||
            method == HttpMethod.Options) {
        return false
    }

    if (transferEncoding != null) return true
    if (connectionType == ConnectionType.Close) return true
    if (contentLength != -1L) {
        return contentLength > 0L
    }

    if (contentType != null) return true

    return false
}

fun expectHttpBody(request: Request): Boolean {
    return expectHttpBody(request.method,
            request.headers["Content-Length"]?.parseDecLong() ?: -1,
            request.headers["Transfer-Encoding"],
            connectionType(request.headers["Connection"]),
            request.headers["Content-Type"]
            )
}

suspend fun parseHttpBody(contentLength: Long,
                          transferEncoding: CharSequence?,
                          connectionType: ConnectionType?,
                          input: ByteReadChannel,
                          out: ByteWriteChannel) {
    if (transferEncoding != null) {
        if (transferEncoding.equalsLowerCase(other = "chunked")) {
            return decodeChunked(input, out)
        } else if (transferEncoding.equalsLowerCase(other = "identity")) {
            // do nothing special
        } else {
            out.close(IOException("Unsupported transfer-encoding $transferEncoding"))
            // TODO: combined transfer encodings
        }
    }

    if (contentLength != -1L) {
        input.copyTo(out, contentLength)
        return
    }

    if (connectionType == ConnectionType.Close) {
        input.copyTo(out)
        return
    }

    out.close(IOException("Failed to parse request body: request body length should be specified, " +
            "chunked transfer encoding should be used or " +
            "keep-alive should be disabled (connection: close)"))
}

suspend fun parseHttpBody(headers: HttpHeadersMap, input: ByteReadChannel, out: ByteWriteChannel) {
    return parseHttpBody(
            headers["Content-Length"]?.parseDecLong() ?: -1,
            headers["Transfer-Encoding"],
            connectionType(headers["Connection"]),
            input, out
    )
}
