package io.ktor.client.engine.android

import io.ktor.cio.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.jvm.javaio.*
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.*

open class AndroidClientEngine(override val config: AndroidEngineConfig) : HttpClientEngine {
    override val dispatcher: CoroutineDispatcher by lazy {
        config.dispatcher ?: Executors.newCachedThreadPool().asCoroutineDispatcher()
    }

    override suspend fun execute(
        call: HttpClientCall, data: HttpRequestData
    ): HttpEngineCall = withContext(dispatcher) {
        val request = AndroidHttpRequest(call, data)
        val response = request.execute()
        HttpEngineCall(request, response)
    }

    override fun close() {
    }

    private suspend fun AndroidHttpRequest.execute(): AndroidHttpResponse {
        val result = CompletableDeferred<AndroidHttpResponse>()
        val requestTime = Date()

        val url = URLBuilder().takeFrom(url).buildString()
        val contentLength = headers[HttpHeaders.ContentLength]?.toLong() ?: content.contentLength

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = config.connectTimeout
            readTimeout = config.socketTimeout

            requestMethod = method.value
            useCaches = false
            instanceFollowRedirects = false

            headers.forEach { key, value ->
                addRequestProperty(key, value.joinToString(";"))
            }

            if (this@execute.content !is OutgoingContent.NoContent) {
                if (contentLength != null) {
                    addRequestProperty(HttpHeaders.ContentLength, contentLength.toString())
                } else {
                    addRequestProperty(HttpHeaders.TransferEncoding, "chunked")
                }

                contentLength?.let { setFixedLengthStreamingMode(it) } ?: setChunkedStreamingMode(0)
                doOutput = true

                this@execute.content.writeTo(outputStream)
            }
        }

        connection.connect()
        val content = connection.content(dispatcher)
        val headerFields = connection.headerFields

        val responseHeaders = HeadersBuilder().apply {
            headerFields?.forEach { (key, values) -> key?.let { appendAll(it, values) } }
        }.build()

        result.complete(
            AndroidHttpResponse(
                call, content, Job(),
                responseHeaders, requestTime, Date(),
                HttpStatusCode.fromValue(connection.responseCode), HttpProtocolVersion.HTTP_1_1,
                connection
            )
        )
        return result.await()
    }
}

internal fun OutgoingContent.writeTo(stream: OutputStream) {
    when (this) {
        is OutgoingContent.ByteArrayContent -> stream.write(bytes())
        is OutgoingContent.ReadChannelContent -> readFrom().toInputStream().copyTo(stream)
        is OutgoingContent.WriteChannelContent -> {
            writer(Unconfined) { writeTo(channel) }.channel.toInputStream().copyTo(stream)
        }
        else -> throw UnsupportedContentTypeException(this)
    }

    stream.close()
}

internal fun HttpURLConnection.content(dispatcher: CoroutineDispatcher): ByteReadChannel = try {
    inputStream?.buffered()
} catch (_: IOException) {
    errorStream?.buffered()
}?.toByteReadChannel(context = dispatcher) ?: ByteReadChannel.Empty
