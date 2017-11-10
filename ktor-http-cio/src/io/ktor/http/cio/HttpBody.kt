package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*
import kotlinx.coroutines.experimental.io.*
import java.io.*

fun expectHttpUpgrade(request: Request): Boolean {
    return request.method == HttpMethod.Get &&
            request.headers[HttpHeaders.Upgrade] != null &&
            request.headers[HttpHeaders.Connection].let { connection ->
                connection != null && connection.contains("upgrade", ignoreCase = true)
            }
}

fun expectHttpBody(request: Request): Boolean {
    val method = request.method
    if (method == HttpMethod.Get ||
            method == HttpMethod.Head ||
            method == HttpMethod.Options) {
        return false
    }

    val hh = request.headers
    val length = hh["Content-Length"]

    if (length != null) {
        if (length.length == 1 && length[0] == '0') return false
        return true
    }

    val transferEncoding = hh["Transfer-Encoding"]
    if (transferEncoding != null) {
        return true
    }

    val connection = hh["Connection"]
    if (connection != null && connection.startsWith("close")) {
        return true
    }

    if (hh["Content-Type"] != null) return true

    return false
}


suspend fun parseHttpBody(headers: HttpHeadersMap, input: ByteReadChannel, out: ByteWriteChannel) {
    val lengthString = headers["Content-Length"]
    if (lengthString != null) {
        val length = lengthString.parseDecLong()

        input.copyTo(out, length)
        return
    }

    val transferEncoding = headers["Transfer-Encoding"]
    if (transferEncoding != null) {
        if (transferEncoding.equalsLowerCase(other = "chunked")) {
            return decodeChunked(input, out)
        } else if (transferEncoding.equalsLowerCase(other = "identity")) {
            // do nothing special
        } else {
            out.close(IOException("Unsupported transfer-encoding $transferEncoding"))
            // TODO unknown transfer encoding?
        }
    }

    if (headers["Connection"]?.equalsLowerCase(other = "close") == true) {
        input.copyTo(out)
        return
    }

    out.close(IOException("Failed to parse request body: request body length should be specified, " +
            "chunked transfer encoding should be used or " +
            "keep-alive should be disabled (connection: close)"))
}
