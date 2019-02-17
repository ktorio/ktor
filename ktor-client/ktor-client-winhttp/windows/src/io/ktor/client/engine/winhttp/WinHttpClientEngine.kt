package io.ktor.client.engine.winhttp

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.winhttp.internal.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlin.coroutines.*

internal class WinHttpClientEngine(override val config: WinHttpClientEngineConfig) : HttpClientEngine {
    override val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    override val coroutineContext: CoroutineContext = dispatcher + SupervisorJob()

    private val processor = WinHttpProcessor(coroutineContext, config)

    override suspend fun execute(
        call: HttpClientCall,
        data: HttpRequestData
    ): HttpEngineCall {
        val callContext = coroutineContext + CompletableDeferred<Unit>()
        val request = DefaultHttpRequest(call, data)
        val requestTime = GMTDate()

        val winHttpRequest = request.toWinHttpRequest()
        val responseData = processor.executeRequest(winHttpRequest)

        println("Processing response: status code ${responseData.status}")

        val response = with(responseData) {
            val headerBytes = ByteReadChannel(headersBytes).apply {
                readUTF8Line()
            }

            val headers = parseHeaders(headerBytes)

            val body = writer(coroutineContext) {
                channel.writeFully(bodyBytes)
            }.channel

            val status = HttpStatusCode.fromValue(status)
            val httpVersion = HttpProtocolVersion.parse(version)

            println("Status $status, version $httpVersion")

            callContext[Job]!!.invokeOnCompletion {
                headers.release()
            }

            WinHttpResponse(
                call, status, CIOHeaders(headers), requestTime,
                body, callContext, httpVersion
            )
        }

        return HttpEngineCall(request, response)
    }

    override fun close() {
        processor.close()
        coroutineContext.cancel()
    }
}

class WinHttpIllegalStateException(cause: String) : IllegalStateException(cause)

class WinHttpRuntimeException(cause: String) : RuntimeException(cause)
