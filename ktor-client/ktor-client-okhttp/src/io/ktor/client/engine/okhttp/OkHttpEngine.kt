package io.ktor.client.engine.okhttp
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.content.*
import io.ktor.util.cio.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import okhttp3.*

class OkHttpEngine(override val config: OkHttpConfig) : HttpClientEngine {
    private val engine = OkHttpClient.Builder()
        .apply(config.config)
        .build()!!

    override val dispatcher: CoroutineDispatcher
        get() = DefaultDispatcher

    override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpEngineCall {
        val request = DefaultHttpRequest(call, data)
        val requestTime = GMTDate()

        val builder = Request.Builder()

        with(builder) {
            url(request.url.toString())

            mergeHeaders(request.headers, request.content) { key, value ->
                addHeader(key, value)
            }

            method(request.method.value, request.content.convertToOkHttpBody())

        }

        val response = engine.execute(builder.build())

        val responseContent = withContext(dispatcher) {
            val body = response.body()
            body?.byteStream()?.toByteReadChannel(context = dispatcher) ?: ByteReadChannel.Empty
        }

        return HttpEngineCall(request, OkHttpResponse(response, call, requestTime, responseContent))
    }

    override fun close() {}
}

internal fun OutgoingContent.convertToOkHttpBody(): RequestBody? = when (this) {
    is OutgoingContent.ByteArrayContent -> RequestBody.create(null, bytes())
    is OutgoingContent.ReadChannelContent -> StreamRequestBody { readFrom() }
    is OutgoingContent.WriteChannelContent -> {
        StreamRequestBody { writer(DefaultDispatcher) { writeTo(channel) }.channel }
    }
    is OutgoingContent.NoContent -> null
    else -> throw UnsupportedContentTypeException(this)
}
