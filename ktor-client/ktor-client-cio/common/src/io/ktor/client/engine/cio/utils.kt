/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import io.ktor.utils.io.errors.EOFException
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal suspend fun HttpRequestData.write(
    output: ByteWriteChannel,
    callContext: CoroutineContext,
    overProxy: Boolean,
    closeChannel: Boolean = true
) {
    try {
        val builder = RequestResponseBuilder()

        val contentLength = headers[HttpHeaders.ContentLength] ?: body.contentLength?.toString()
        val contentEncoding = headers[HttpHeaders.TransferEncoding]
        val responseEncoding = body.headers[HttpHeaders.TransferEncoding]
        val chunked = contentLength == null || responseEncoding == "chunked" || contentEncoding == "chunked"

        try {
            val urlString = if (overProxy) {
                url.toString()
            } else {
                url.fullPath
            }

            builder.requestLine(method, urlString, HttpProtocolVersion.HTTP_1_1.toString())
            // this will only add the port to the host header if the port is non-standard for the protocol
            if (!headers.contains(HttpHeaders.Host)) {
                val host = if (url.protocol.defaultPort == url.port) {
                    url.host
                } else {
                    url.hostWithPort
                }
                builder.headerLine(HttpHeaders.Host, host)
            }

            if (contentLength != null) {
                if ((method != HttpMethod.Get && method != HttpMethod.Head) || body !is OutgoingContent.NoContent) {
                    builder.headerLine(HttpHeaders.ContentLength, contentLength)
                }
            }

            mergeHeaders(headers, body) { key, value ->
                if (key == HttpHeaders.ContentLength) return@mergeHeaders

                builder.headerLine(key, value)
            }

            if (chunked && contentEncoding == null && responseEncoding == null && body !is OutgoingContent.NoContent) {
                builder.headerLine(HttpHeaders.TransferEncoding, "chunked")
            }

            builder.emptyLine()
            output.writePacket(builder.build())
            output.flush()
        } finally {
            builder.release()
        }

        val content = body
        if (content is OutgoingContent.NoContent) {
            return
        }

        val chunkedJob: EncoderJob? = if (chunked) encodeChunked(output, callContext) else null
        val channel = chunkedJob?.channel ?: output

        try {
            when (content) {
                is OutgoingContent.NoContent -> return
                is OutgoingContent.ByteArrayContent -> channel.writeFully(content.bytes())
                is OutgoingContent.ReadChannelContent -> content.readFrom().copyAndClose(channel)
                is OutgoingContent.WriteChannelContent -> content.writeTo(channel)
                is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(content)
            }
        } catch (cause: Throwable) {
            channel.close(cause)
        } finally {
            channel.flush()
            chunkedJob?.channel?.close()
            chunkedJob?.join()
        }
    } finally {
        output.closedCause?.let { throw it }
        if (closeChannel) {
            output.close()
        }
    }
}

internal suspend fun readResponse(
    requestTime: GMTDate,
    request: HttpRequestData,
    input: ByteReadChannel,
    output: ByteWriteChannel,
    callContext: CoroutineContext
): HttpResponseData {
    val rawResponse = parseResponse(input)
        ?: throw EOFException("Failed to parse HTTP response: unexpected EOF")

    rawResponse.use {
        val status = HttpStatusCode(rawResponse.status, rawResponse.statusText.toString())
        val contentLength = rawResponse.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L
        val transferEncoding = rawResponse.headers[HttpHeaders.TransferEncoding]?.toString()
        val connectionType = ConnectionOptions.parse(rawResponse.headers[HttpHeaders.Connection])

        val rawHeaders = rawResponse.headers
        val headers = HeadersImpl(rawHeaders.toMap())
        val version = HttpProtocolVersion.parse(rawResponse.version)

        if (status == HttpStatusCode.SwitchingProtocols) {
            return startWebSocketSession(status, requestTime, headers, version, callContext, input, output)
        }

        val body = when {
            request.method == HttpMethod.Head ||
                status in listOf(HttpStatusCode.NotModified, HttpStatusCode.NoContent) ||
                status.isInformational() -> {
                ByteReadChannel.Empty
            }
            else -> {
                val httpBodyParser = GlobalScope.writer(Dispatchers.Unconfined, autoFlush = true) {
                    parseHttpBody(contentLength, transferEncoding, connectionType, input, channel)
                }

                httpBodyParser.channel
            }
        }

        return HttpResponseData(status, requestTime, headers, version, body, callContext)
    }
}

internal suspend fun startTunnel(
    request: HttpRequestData,
    output: ByteWriteChannel,
    input: ByteReadChannel
) {
    val builder = RequestResponseBuilder()

    try {
        builder.requestLine(HttpMethod("CONNECT"), request.url.hostWithPort, HttpProtocolVersion.HTTP_1_1.toString())
        // this will only add the port to the host header if the port is non-standard for the protocol
        val host = if (request.url.protocol.defaultPort == request.url.port) {
            request.url.host
        } else {
            request.url.hostWithPort
        }

        builder.headerLine(HttpHeaders.Host, host)
        builder.headerLine("Proxy-Connection", "Keep-Alive") // For HTTP/1.0 proxies like Squid.
        request.headers[HttpHeaders.UserAgent]?.let {
            builder.headerLine(HttpHeaders.UserAgent, it)
        }
        request.headers[HttpHeaders.ProxyAuthenticate]?.let {
            builder.headerLine(HttpHeaders.ProxyAuthenticate, it)
        }
        request.headers[HttpHeaders.ProxyAuthorization]?.let {
            builder.headerLine(HttpHeaders.ProxyAuthorization, it)
        }

        builder.emptyLine()
        output.writePacket(builder.build())
        output.flush()

        val rawResponse = parseResponse(input)
            ?: throw EOFException("Failed to parse CONNECT response: unexpected EOF")
        rawResponse.use {
            if (rawResponse.status / 200 != 1) {
                throw IOException("Can not establish tunnel connection")
            }
            rawResponse.headers[HttpHeaders.ContentLength]?.let {
                input.discard(it.toString().toLong())
            }
        }
    } finally {
        builder.release()
    }
}

internal fun HttpHeadersMap.toMap(): Map<String, List<String>> {
    val result = mutableMapOf<String, MutableList<String>>()

    for (index in 0 until size) {
        val key = nameAt(index).toString()
        val value = valueAt(index).toString()

        if (result[key]?.add(value) == null) {
            result[key] = mutableListOf(value)
        }
    }

    return result
}

internal fun HttpStatusCode.isInformational(): Boolean = (value / 100) == 1

/**
 * Wrap channel so that [ByteWriteChannel.close] of the resulting channel doesn't lead to closing of the base channel.
 */
internal fun ByteWriteChannel.withoutClosePropagation(
    coroutineContext: CoroutineContext,
    closeOnCoroutineCompletion: Boolean = true
): ByteWriteChannel {
    if (closeOnCoroutineCompletion) {
        // Pure output represents a socket output channel that is closed when request fully processed or after
        // request sent in case TCP half-close is allowed.
        coroutineContext[Job]!!.invokeOnCompletion {
            close()
        }
    }

    return GlobalScope.reader(coroutineContext, autoFlush = true) {
        channel.copyTo(this@withoutClosePropagation, Long.MAX_VALUE)
        this@withoutClosePropagation.flush()
    }.channel
}

/**
 * Wrap channel using [withoutClosePropagation] if [propagateClose] is false otherwise return the same channel.
 */
internal fun ByteWriteChannel.handleHalfClosed(
    coroutineContext: CoroutineContext,
    propagateClose: Boolean
): ByteWriteChannel = if (propagateClose) this else withoutClosePropagation(coroutineContext)
