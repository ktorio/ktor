package io.ktor.client.engine.okhttp

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.jvm.javaio.*
import okhttp3.*
import kotlin.coroutines.*

@InternalAPI
@Suppress("KDocMissingDocumentation")
class OkHttpEngine(
    override val config: OkHttpConfig
) : HttpClientJvmEngine("ktor-okhttp"), WebSocketEngine {

    private val engine = config.preconfigured ?: OkHttpClient.Builder()
        .apply(config.config)
        .build()!!

    override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpEngineCall {
        val request = DefaultHttpRequest(call, data)
        val callContext = createCallContext()
        val engineRequest = request.convertToOkHttpRequest(callContext)
        val response = executeHttpRequest(engineRequest, callContext, call)

        return HttpEngineCall(request, response)
    }

    override suspend fun execute(request: HttpRequest): WebSocketResponse {
        check(request.url.protocol.isWebsocket())

        val callContext = createCallContext()
        val pingInterval = engine.pingIntervalMillis().toLong()
        val requestTime = GMTDate()
        val engineRequest = request.convertToOkHttpRequest(callContext)

        val session = OkHttpWebsocketSession(engine, engineRequest, callContext)
        return WebSocketResponse(callContext, requestTime, session)
    }

    private suspend fun executeHttpRequest(
        engineRequest: Request,
        callContext: CoroutineContext,
        call: HttpClientCall
    ): HttpResponse {
        val requestTime = GMTDate()
        val response = engine.execute(engineRequest)

        val body = response.body()
        callContext[Job]?.invokeOnCompletion { body?.close() }

        val responseContent = withContext(callContext) {
            body?.byteStream()?.toByteReadChannel(
                context = callContext,
                pool = KtorDefaultPool
            ) ?: ByteReadChannel.Empty
        }

        return OkHttpResponse(response, call, requestTime, responseContent, callContext)
    }
}

private fun HttpRequest.convertToOkHttpRequest(callContext: CoroutineContext): Request {
    val builder = Request.Builder()

    with(builder) {
        url(url.toString())

        mergeHeaders(headers, content) { key, value ->
            addHeader(key, value)
        }

        method(method.value, content.convertToOkHttpBody(callContext))
    }

    return builder.build()!!
}

internal fun OutgoingContent.convertToOkHttpBody(callContext: CoroutineContext): RequestBody? = when (this) {
    is OutgoingContent.ByteArrayContent -> RequestBody.create(null, bytes())
    is OutgoingContent.ReadChannelContent -> StreamRequestBody(contentLength) { readFrom() }
    is OutgoingContent.WriteChannelContent -> {
        StreamRequestBody(contentLength) { GlobalScope.writer(callContext) { writeTo(channel) }.channel }
    }
    is OutgoingContent.NoContent -> null
    else -> throw UnsupportedContentTypeException(this)
}
