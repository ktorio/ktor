package io.ktor.client.engine.android

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.cio.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.jvm.javaio.*
import java.io.*
import java.lang.IllegalStateException
import java.net.*
import javax.net.ssl.*
import kotlin.coroutines.*

/**
 * Android client engine
 */
class AndroidClientEngine(override val config: AndroidEngineConfig) : HttpClientJvmEngine("ktor-android") {

    override suspend fun execute(
        call: HttpClientCall, data: HttpRequestData
    ): HttpEngineCall = withContext(coroutineContext) {
        val callContext = createCallContext()

        @Suppress("UNCHECKED_CAST")
        val userContext = data.executionContext as CompletableDeferred<Unit>
        callContext[Job]!!.invokeOnCompletion {
            if (it == null) userContext.complete(Unit) else userContext.completeExceptionally(it)
        }

        val request = AndroidHttpRequest(call, data)
        val response = request.execute(callContext)
        HttpEngineCall(request, response)
    }

    private fun AndroidHttpRequest.execute(callContext: CoroutineContext): AndroidHttpResponse {
        val requestTime = GMTDate()

        val url = URLBuilder().takeFrom(url).buildString()
        val outgoingContent = this@execute.content
        val contentLength = headers[HttpHeaders.ContentLength]?.toLong() ?: outgoingContent.contentLength

        val connection = getProxyAwareConnection(url).apply {
            connectTimeout = config.connectTimeout
            readTimeout = config.socketTimeout

            if (this is HttpsURLConnection) {
                config.sslManager(this)
            }

            requestMethod = method.value
            useCaches = false
            instanceFollowRedirects = false

            mergeHeaders(headers, outgoingContent) { key, value ->
                addRequestProperty(key, value)
            }

            if (outgoingContent !is OutgoingContent.NoContent) {
                if (method in listOf(HttpMethod.Get, HttpMethod.Head)) throw RequestInvalidException(
                    "Request of type ${method.value} coudn't send body with [Android] engine"
                )

                if (contentLength != null) {
                    addRequestProperty(HttpHeaders.ContentLength, contentLength.toString())
                } else {
                    addRequestProperty(HttpHeaders.TransferEncoding, "chunked")
                }

                contentLength?.let { setFixedLengthStreamingMode(it.toInt()) } ?: setChunkedStreamingMode(0)
                doOutput = true

                outgoingContent.writeTo(outputStream, callContext)
            }
        }

        connection.connect()
        val content = connection.content(callContext)
        val headerFields = connection.headerFields

        val responseHeaders = HeadersBuilder().apply {
            headerFields?.forEach { (key, values) -> key?.let { appendAll(it, values) } }
        }.build()

        return AndroidHttpResponse(
            call, content,
            responseHeaders, requestTime, GMTDate(),
            HttpStatusCode(connection.responseCode, connection.responseMessage), HttpProtocolVersion.HTTP_1_1,
            callContext,
            connection
        )
    }

    private fun getProxyAwareConnection(url: String): HttpURLConnection {
        val u = URL(url)
        val connection = config.proxy?.let { u.openConnection(it) } ?: u.openConnection()
        return connection as HttpURLConnection
    }
}

internal fun OutgoingContent.writeTo(
    stream: OutputStream, callContext: CoroutineContext
): Unit = stream.use {
    when (this) {
        is OutgoingContent.ByteArrayContent -> it.write(bytes())
        is OutgoingContent.ReadChannelContent -> readFrom().toInputStream(callContext[Job]).copyTo(it)
        is OutgoingContent.WriteChannelContent -> {
            GlobalScope.writer(callContext) { writeTo(channel) }.channel.toInputStream(callContext[Job]).copyTo(it)
        }
        else -> throw UnsupportedContentTypeException(this)
    }
}

internal fun HttpURLConnection.content(callScope: CoroutineContext): ByteReadChannel = try {
    inputStream?.buffered()
} catch (_: IOException) {
    errorStream?.buffered()
}?.toByteReadChannel(context = callScope, pool = KtorDefaultPool) ?: ByteReadChannel.Empty

@Suppress("KDocMissingDocumentation")
class RequestInvalidException(override val message: String): IllegalStateException()
