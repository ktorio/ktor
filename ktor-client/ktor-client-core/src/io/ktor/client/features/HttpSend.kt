package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.content.*
import io.ktor.util.*

typealias HttpSendInterceptor = suspend Sender.(HttpClientCall) -> HttpClientCall

interface Sender {
    suspend fun execute(requestBuilder: HttpRequestBuilder): HttpClientCall
}

class HttpSend(
    var maxSendCount: Int = 20
) {
    private val interceptors: MutableList<HttpSendInterceptor> = mutableListOf()

    fun intercept(block: HttpSendInterceptor) {
        interceptors += block
    }

    companion object Feature : HttpClientFeature<HttpSend, HttpSend> {
        override val key: AttributeKey<HttpSend> = AttributeKey("HttpSend")

        override fun prepare(block: HttpSend.() -> Unit): HttpSend = HttpSend().apply(block)

        override fun install(feature: HttpSend, scope: HttpClient) {
            // default send scenario
            scope.requestPipeline.intercept(HttpRequestPipeline.Send) { content ->
                if (content !is OutgoingContent) return@intercept
                context.body = content

                var sent =  0
                val sender = object : Sender {
                    override suspend fun execute(requestBuilder: HttpRequestBuilder): HttpClientCall {
                        if (sent >= feature.maxSendCount) throw SendCountExceed()
                        sent++
                        return scope.sendPipeline.execute(requestBuilder, requestBuilder.body) as HttpClientCall
                    }
                }

                var currentCall = sender.execute(context)
                while (true) {
                    loop@for (interceptor in feature.interceptors) {
                        val transformed = interceptor(sender, currentCall)
                        if (transformed === currentCall) continue@loop

                        currentCall = transformed
                        break@loop
                    }

                    break
                }

                proceedWith(currentCall)
            }
        }
    }
}

class SendCountExceed : IllegalStateException()
