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
) : HttpClientJvmEngine("ktor-okhttp") {

    private val engine: OkHttpClient = config.preconfigured
        ?: OkHttpClient.Builder().apply(config.config).build()

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = createCallContext()
        val engineRequest = data.convertToOkHttpRequest(callContext)

        return if (data.isUpgradeRequest()) {
            executeWebSocketRequest(engineRequest, callContext)
        } else {
            executeHttpRequest(engineRequest, callContext)
        }
    }

    override fun close() {
        super.close()

        coroutineContext[Job]?.invokeOnCompletion {
            engine.dispatcher().executorService().shutdown()
            engine.connectionPool().evictAll()
            engine.cache()?.close()
        }
    }

    private suspend fun executeWebSocketRequest(
        engineRequest: Request,
        callContext: CoroutineContext
    ): HttpResponseData {
        val requestTime = GMTDate()
        val session = OkHttpWebsocketSession(engine, engineRequest, callContext)

        val originResponse = session.originResponse.await()
        return buildResponseData(originResponse, requestTime, session, callContext)
    }

    private suspend fun executeHttpRequest(
        engineRequest: Request,
        callContext: CoroutineContext
    ): HttpResponseData {
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

        return buildResponseData(response, requestTime, responseContent, callContext)

    }

    private fun buildResponseData(
        response: Response, requestTime: GMTDate, body: Any, callContext: CoroutineContext
    ): HttpResponseData {
        val status = HttpStatusCode(response.code(), response.message())
        val version = response.protocol().fromOkHttp()
        val headers = response.headers().fromOkHttp()

        return HttpResponseData(status, requestTime, headers, version, body, callContext)
    }
}

private fun HttpRequestData.convertToOkHttpRequest(callContext: CoroutineContext): Request {
    val builder = Request.Builder()

    with(builder) {
        url(url.toString())

        mergeHeaders(headers, body) { key, value ->
            addHeader(key, value)
        }

        method(method.value, body.convertToOkHttpBody(callContext))
    }

    return builder.build()
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
