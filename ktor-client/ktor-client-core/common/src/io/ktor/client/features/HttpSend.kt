/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.concurrent.*
import kotlinx.coroutines.*

/**
 * HttpSend pipeline interceptor function
 */
public typealias HttpSendInterceptor = suspend Sender.(HttpClientCall, HttpRequestBuilder) -> HttpClientCall

/**
 * HttpSend pipeline interceptor function backward compatible with previous implementation.
 */
public typealias HttpSendInterceptorBackwardCompatible = suspend Sender.(HttpClientCall) -> HttpClientCall

/**
 * This interface represents a request send pipeline interceptor chain
 */
public interface Sender {
    /**
     * Execute send pipeline. It could start pipeline execution or replace the call
     */
    public suspend fun execute(requestBuilder: HttpRequestBuilder): HttpClientCall
}

/**
 * This is internal feature that is always installed.
 * @property maxSendCount is a maximum number of requests that can be sent during a call
 */
public class HttpSend(
    maxSendCount: Int = 20
) {
    public var maxSendCount: Int by shared(maxSendCount)

    private val interceptors: MutableList<HttpSendInterceptor> = sharedList()

    init {
        makeShared()
    }

    /**
     * Install send pipeline starter interceptor
     */
    public fun intercept(block: HttpSendInterceptor) {
        interceptors += block
    }

    /**
     * Install send pipeline starter interceptor (backward compatible function).
     */
    @Deprecated("Intercept with one parameter is deprecated, use both call and request builder as parameters.")
    public fun intercept(block: HttpSendInterceptorBackwardCompatible) {
        interceptors += { call, _ ->
            block(call)
        }
    }

    /**
     * Feature installation object
     */
    public companion object Feature : HttpClientFeature<HttpSend, HttpSend> {
        override val key: AttributeKey<HttpSend> = AttributeKey("HttpSend")

        override fun prepare(block: HttpSend.() -> Unit): HttpSend = HttpSend().apply(block)

        override fun install(feature: HttpSend, scope: HttpClient) {
            // default send scenario
            scope.requestPipeline.intercept(HttpRequestPipeline.Send) { content ->
                check(content is OutgoingContent) {
                    """
|Fail to serialize body. Content has type: ${content::class}, but OutgoingContent expected.
|If you expect serialized body, please check that you have installed the corresponding feature(like `Json`) and set `Content-Type` header."""
                        .trimMargin()
                }
                context.body = content

                val sender = DefaultSender(feature.maxSendCount, scope)
                var currentCall = sender.execute(context)
                var callChanged: Boolean

                do {
                    callChanged = false

                    passInterceptors@ for (interceptor in feature.interceptors) {
                        val transformed = interceptor(sender, currentCall, context)
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

    private class DefaultSender(
        private val maxSendCount: Int,
        private val client: HttpClient
    ) : Sender {
        private var sentCount: Int = 0
        private var currentCall: HttpClientCall? = null

        override suspend fun execute(requestBuilder: HttpRequestBuilder): HttpClientCall {
            currentCall?.cancel()

            if (sentCount >= maxSendCount) {
                throw SendCountExceedException(
                    "Max send count $maxSendCount exceeded. Consider increasing the property " +
                        "maxSendCount if more is required."
                )
            }

            sentCount++
            val sendResult = client.sendPipeline.execute(
                requestBuilder,
                requestBuilder.body
            )

            val call = sendResult as? HttpClientCall
                ?: error("Failed to execute send pipeline. Expected [HttpClientCall], but received $sendResult")

            currentCall = call
            return call
        }
    }
}

/**
 * Thrown when too many actual requests were sent during a client call.
 * It could be caused by infinite or too long redirect sequence.
 * Maximum number of requests is limited by [HttpSend.maxSendCount]
 */
public class SendCountExceedException(message: String) : IllegalStateException(message)
