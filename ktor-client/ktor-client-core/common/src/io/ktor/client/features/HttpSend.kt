package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.content.*
import io.ktor.util.*

/**
 * HttpSend pipeline interceptor function
 */
typealias HttpSendInterceptor = suspend Sender.(HttpClientCall) -> HttpClientCall

/**
 * This interface represents a request send pipeline interceptor chain
 */
@KtorExperimentalAPI
interface Sender {
    /**
     * Execute send pipeline. It could start pipeline execution or replace the call
     */
    suspend fun execute(requestBuilder: HttpRequestBuilder): HttpClientCall
}

/**
 * This is internal feature that is always installed.
 * @property maxSendCount is a maximum number of requests that can be sent during a call
 */
@KtorExperimentalAPI
class HttpSend(
    var maxSendCount: Int = 20
) {
    private val interceptors: MutableList<HttpSendInterceptor> = mutableListOf()

    /**
     * Install send pipeline starter interceptor
     */
    fun intercept(block: HttpSendInterceptor) {
        interceptors += block
    }

    /**
     * Feature installation object
     */
    companion object Feature : HttpClientFeature<HttpSend, HttpSend> {
        override val key: AttributeKey<HttpSend> = AttributeKey("HttpSend")

        override fun prepare(block: HttpSend.() -> Unit): HttpSend = HttpSend().apply(block)

        override fun install(feature: HttpSend, scope: HttpClient) {
            // default send scenario
            scope.requestPipeline.intercept(HttpRequestPipeline.Send) { content ->
                if (content !is OutgoingContent) return@intercept
                context.body = content

                val sender = DefaultSender(feature.maxSendCount, scope)

                var currentCall = sender.execute(context)
                var callChanged: Boolean

                do {
                    callChanged = false

                    passInterceptors@ for (interceptor in feature.interceptors) {
                        val transformed = interceptor(sender, currentCall)
                        if (transformed === currentCall) continue@passInterceptors

                        currentCall = transformed
                        callChanged = true
                        break@passInterceptors
                    }
                } while (callChanged)

                proceedWith(currentCall)
            }
        }
    }

    private class DefaultSender(private val maxSendCount: Int, private val client: HttpClient) : Sender {
        private var sentCount: Int = 0

        override suspend fun execute(requestBuilder: HttpRequestBuilder): HttpClientCall {
            if (sentCount >= maxSendCount) throw SendCountExceedException("Max send count $maxSendCount exceeded")
            sentCount++

            return client.sendPipeline.execute(requestBuilder, requestBuilder.body) as HttpClientCall
        }
    }
}

/**
 * Thrown when too many actual requests were sent during a client call.
 * It could be caused by infinite or too long redirect sequence.
 * Maximum number of requests is limited by [HttpSend.maxSendCount]
 */
class SendCountExceedException(message: String) : IllegalStateException(message)
