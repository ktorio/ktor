package io.ktor.client.engine.urlfetch

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.util.cio.KtorDefaultPool
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.jvm.javaio.copyTo
import kotlinx.coroutines.io.jvm.javaio.toByteReadChannel
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.*

class UrlFetchEngine(override val config: UrlFetchConfig) : HttpClientJvmEngine("ktor-urlfetch") {

    override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpEngineCall {
        val request = DefaultHttpRequest(call, data)
        val requestTime = GMTDate()
        val callContext = createCallContext()

        val url = URL(request.url.toString())
        val connection = url.openConnection() as HttpURLConnection
        config.applyOn(connection)

        with(connection) {
            requestMethod = request.method.value
            mergeHeaders(request.headers, request.content) { key, value ->
                setRequestProperty(key, value)
            }

            request.content.writeToConnection(callContext, connection)

            val status = HttpStatusCode.fromValue(responseCode)

            val responseStream = if (status.isSuccess()) inputStream else errorStream

            val content = withContext(callContext) {
                responseStream?.toByteReadChannel(
                    context = callContext,
                    pool = KtorDefaultPool
                ) ?: ByteReadChannel.Empty
            }
            return HttpEngineCall(
                request,
                UrlFetchResponse(status, call, requestTime, callContext, content, getHeaders())
            )
        }
    }


}

private fun HttpURLConnection.getHeaders() = Headers.build {
    headerFields.forEach { key, valuesList -> valuesList.forEach { key?.let { key -> append(key, it) } } }
}

private suspend fun OutgoingContent.writeToConnection(callContext: CoroutineContext, connection: HttpURLConnection) {
    when (this) {
        is OutgoingContent.NoContent -> {
        }
        is OutgoingContent.ReadChannelContent -> {
            with(connection) {
                doOutput = true
                readFrom().copyTo(DataOutputStream(outputStream))
            }
        }
        is OutgoingContent.ByteArrayContent -> {
            with(connection) {
                doOutput = true
                DataOutputStream(outputStream).use {
                    it.write(bytes())
                    it.flush()
                }
            }
        }
        is OutgoingContent.WriteChannelContent -> {
            with(connection) {
                doOutput = true
                GlobalScope.writer(callContext) { writeTo(channel) }.channel
                    .copyTo(DataOutputStream(outputStream))

            }
        }
        else -> throw UnsupportedContentTypeException(this)
    }
}
