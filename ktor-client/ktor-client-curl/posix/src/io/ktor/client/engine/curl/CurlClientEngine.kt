package io.ktor.client.engine.curl

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.curl.internal.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import kotlin.coroutines.*

internal class CurlClientEngine(override val config: CurlClientEngineConfig) : HttpClientEngine {
    override val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    override val coroutineContext: CoroutineContext = dispatcher + SupervisorJob()

    private val curlProcessor = CurlProcessor(coroutineContext)

    override suspend fun execute(
        call: HttpClientCall,
        data: HttpRequestData
    ): HttpEngineCall {
        val callContext = coroutineContext + CompletableDeferred<Unit>()
        val request = DefaultHttpRequest(call, data)
        val requestTime = GMTDate()

        val curlRequest = request.toCurlRequest()
        val responseData = curlProcessor.executeRequest(curlRequest)

        val response = with(responseData) {
            val headers = parseHeaders(ByteReadChannel(headersBytes))

            val body = writer {
                channel.writeFully(bodyBytes)
            }.channel

            val status = HttpStatusCode.fromValue(status)

            callContext[Job]!!.invokeOnCompletion {
                headers.release()
            }

            CurlHttpResponse(
                call, status, CIOHeaders(headers), requestTime,
                body, callContext, version.fromCurl()
            )
        }

        return HttpEngineCall(request, response)
    }

    override fun close() {
        curlProcessor.close()
        coroutineContext.cancel()
    }
}

class CurlIllegalStateException(cause: String) : IllegalStateException(cause)

class CurlRuntimeException(cause: String) : RuntimeException(cause)
