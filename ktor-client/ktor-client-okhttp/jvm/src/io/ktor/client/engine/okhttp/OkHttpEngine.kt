package io.ktor.client.engine.okhttp
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.content.*
import io.ktor.util.cio.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.jvm.javaio.*
import okhttp3.*
import kotlin.coroutines.*

class OkHttpEngine(override val config: OkHttpConfig) : HttpClientJvmEngine("ktor-okhttp") {
    private val engine = OkHttpClient.Builder()
        .apply(config.config)
        .build()!!

    override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpEngineCall {
        val request = DefaultHttpRequest(call, data)
        val requestTime = GMTDate()
        val callContext = createCallContext()

        val builder = Request.Builder()

        with(builder) {
            url(request.url.toString())

            mergeHeaders(request.headers, request.content) { key, value ->
                addHeader(key, value)
            }

            method(request.method.value, request.content.convertToOkHttpBody(callContext))
        }

        val response = engine.execute(builder.build())

        val body = response.body()
        callContext[Job]?.invokeOnCompletion { body?.close() }

        val responseContent = withContext(callContext) {
            body?.byteStream()?.toByteReadChannel(
                context = callContext,
                pool = KtorDefaultPool
            ) ?: ByteReadChannel.Empty
        }

        return HttpEngineCall(request, OkHttpResponse(response, call, requestTime, responseContent, callContext))
    }
}

internal fun OutgoingContent.convertToOkHttpBody(callContext: CoroutineContext): RequestBody? = when (this) {
    is OutgoingContent.ByteArrayContent -> RequestBody.create(null, bytes())
    is OutgoingContent.ReadChannelContent -> StreamRequestBody { readFrom() }
    is OutgoingContent.WriteChannelContent -> {
        StreamRequestBody { GlobalScope.writer(callContext) { writeTo(channel) }.channel }
    }
    is OutgoingContent.NoContent -> null
    else -> throw UnsupportedContentTypeException(this)
}
