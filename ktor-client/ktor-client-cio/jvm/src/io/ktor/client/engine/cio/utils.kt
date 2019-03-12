package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.io.errors.*
import kotlin.coroutines.*

internal suspend fun HttpRequestData.write(output: ByteWriteChannel, callContext: CoroutineContext) {
    val builder = RequestResponseBuilder()

    val contentLength = headers[HttpHeaders.ContentLength] ?: body.contentLength?.toString()
    val contentEncoding = headers[HttpHeaders.TransferEncoding]
    val responseEncoding = body.headers[HttpHeaders.TransferEncoding]
    val chunked = contentLength == null || responseEncoding == "chunked" || contentEncoding == "chunked"

    try {
        builder.requestLine(method, url.fullPath, HttpProtocolVersion.HTTP_1_1.toString())
        builder.headerLine("Host", url.hostWithPort)

        mergeHeaders(headers, body) { key, value ->
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
    if (content is OutgoingContent.NoContent)
        return

    val chunkedJob: EncoderJob? = if (chunked) encodeChunked(output, callContext) else null
    val channel = chunkedJob?.channel ?: output

    try {
        when (content) {
            is OutgoingContent.NoContent -> return
            is OutgoingContent.ByteArrayContent -> channel.writeFully(content.bytes())
            is OutgoingContent.ReadChannelContent -> content.readFrom().copyAndClose(channel)
            is OutgoingContent.WriteChannelContent -> content.writeTo(channel)
            is OutgoingContent.ProtocolUpgrade -> UnsupportedContentTypeException(content)
        }
    } catch (cause: Throwable) {
        channel.close(cause)
    } finally {
        channel.flush()
        chunkedJob?.channel?.close()
        chunkedJob?.join()
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

    val status = HttpStatusCode(rawResponse.status, rawResponse.statusText.toString())
    val contentLength = rawResponse.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L
    val transferEncoding = rawResponse.headers[HttpHeaders.TransferEncoding]
    val connectionType = ConnectionOptions.parse(rawResponse.headers[HttpHeaders.Connection])
    val headers = CIOHeaders(rawResponse.headers)
    val version = HttpProtocolVersion.parse(rawResponse.version)

    callContext[Job]!!.invokeOnCompletion {
        rawResponse.headers.release()
    }

    if (status == HttpStatusCode.SwitchingProtocols) {
        val session = RawWebSocket(input, output, masking = true, coroutineContext = callContext)
        return HttpResponseData(status, requestTime, headers, version, session, callContext)
    }

    val body = when {
        request.method == HttpMethod.Head ||
            status in listOf(HttpStatusCode.NotModified, HttpStatusCode.NoContent) ||
            status.value / 100 == 1 -> {
            ByteReadChannel.Empty
        }
        else -> {
            val httpBodyParser = GlobalScope.writer(callContext, autoFlush = true) {
                parseHttpBody(contentLength, transferEncoding, connectionType, input, channel)
            }

            httpBodyParser.channel
        }
    }

    return HttpResponseData(status, requestTime, headers, version, body, callContext)
}
