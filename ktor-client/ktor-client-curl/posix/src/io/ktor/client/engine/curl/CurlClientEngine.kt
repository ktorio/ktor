package io.ktor.client.engine.curl

import io.ktor.client.engine.*
import io.ktor.client.engine.curl.internal.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlin.coroutines.*

internal class CurlClientEngine(override val config: CurlClientEngineConfig) : HttpClientEngine {
    override val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    override val coroutineContext: CoroutineContext = dispatcher + SupervisorJob()

    private val curlProcessor = CurlProcessor(coroutineContext)

    override suspend fun execute(
        data: HttpRequestData
    ): HttpResponseData {
        val callContext = coroutineContext + Job()
        val requestTime = GMTDate()

        val curlRequest = data.toCurlRequest()
        val responseData = curlProcessor.executeRequest(curlRequest)

        return with(responseData) {
            val headerBytes = ByteReadChannel(headersBytes).apply {
                readUTF8Line()
            }
            val headers = parseHeaders(headerBytes)

            val body = writer(coroutineContext) {
                channel.writeFully(bodyBytes)
            }.channel

            val status = HttpStatusCode.fromValue(status)

            callContext[Job]!!.invokeOnCompletion {
                headers.release()
            }

            HttpResponseData(
                status, requestTime, CIOHeaders(headers), version.fromCurl(),
                body, callContext
            )
        }
    }

    override fun close() {
        curlProcessor.close()
        coroutineContext.cancel()
    }
}

@Suppress("KDocMissingDocumentation")
class CurlIllegalStateException(cause: String) : IllegalStateException(cause)

@Suppress("KDocMissingDocumentation")
class CurlRuntimeException(cause: String) : RuntimeException(cause)
