package io.ktor.client.engine.winhttp

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.winhttp.internal.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.util.date.*
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlin.coroutines.*
import kotlin.math.min

internal class WinHttpClientEngine(override val config: WinHttpClientEngineConfig) : HttpClientEngine {
    override val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    override val coroutineContext: CoroutineContext = dispatcher + SupervisorJob()

    override suspend fun execute(
        call: HttpClientCall,
        data: HttpRequestData
    ): HttpEngineCall {
        val callContext = coroutineContext + CompletableDeferred<Unit>()
        val request = DefaultHttpRequest(call, data)
        val requestTime = GMTDate()

        val response = WinHttpSession().use { session ->
            session.setTimeouts(
                config.resolveTimeout,
                config.connectTimeout,
                config.sendTimeout,
                config.receiveTimeout
            )

            session.createRequest(request.method, request.url).use { httpRequest ->
                httpRequest.addHeaders(request.headersToList())

                httpRequest.sendAsync().await()

                request.content.toByteArray()?.let { body ->
                    body.usePinned {
                        httpRequest.writeBodyAsync(it).await()
                    }
                }

                val responseData = httpRequest.readResponseAsync().await()
                with(responseData) {
                    val status = HttpStatusCode.fromValue(status)
                    val httpVersion = HttpProtocolVersion.parse(version)

                    val headerBytes = ByteReadChannel(headers).apply {
                        readUTF8Line()
                    }

                    val headers = parseHeaders(headerBytes)

                    val body = writer(coroutineContext) {
                        while (true) {
                            val dataAvailable = httpRequest.queryDataAvailableAsync().await()
                            if (dataAvailable > 0) {
                                val bufferSize = min(dataAvailable, Int.MAX_VALUE.toLong()).toInt()
                                val buffer = ByteArray(bufferSize)
                                val size = buffer.usePinned {
                                    httpRequest.readDataAsync(it).await()
                                }
                                channel.writeFully(buffer, 0, size)
                            } else break
                        }
                    }.channel

                    callContext[Job]!!.invokeOnCompletion {
                        headers.release()
                    }

                    WinHttpResponse(
                        call, status, CIOHeaders(headers), requestTime,
                        body, callContext, httpVersion
                    )
                }
            }
        }

        return HttpEngineCall(request, response)
    }

    override fun close() {
        coroutineContext.cancel()
    }
}

class WinHttpIllegalStateException(cause: String) : IllegalStateException(cause)
