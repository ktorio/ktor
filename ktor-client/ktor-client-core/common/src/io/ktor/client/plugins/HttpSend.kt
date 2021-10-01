/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

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
public typealias HttpSendInterceptor = suspend Sender.(HttpRequestBuilder) -> HttpClientCall

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
 * This is internal plugin that is always installed.
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
    @Deprecated(
        "This interceptors do not allow to intercept first network call. " +
            "Please use another overload and replace HttpClientCall parameter using `val call = execute(request)`",
        level = DeprecationLevel.ERROR
    )
    @Suppress("UNUSED_PARAMETER")
    public fun intercept(block: suspend Sender.(HttpClientCall, HttpRequestBuilder) -> HttpClientCall) {
        error(
            "This interceptors do not allow to intercept original call. " +
                "Please use another overload and call `this.execute(request)` manually"
        )
    }

    /**
     * Install send pipeline starter interceptor
     */
    public fun intercept(block: HttpSendInterceptor) {
        interceptors += block
    }

    /**
     * Plugin installation object
     */
    public companion object Plugin : HttpClientPlugin<HttpSend, HttpSend> {
        override val key: AttributeKey<HttpSend> = AttributeKey("HttpSend")

        override fun prepare(block: HttpSend.() -> Unit): HttpSend = HttpSend().apply(block)

        override fun install(plugin: HttpSend, scope: HttpClient) {
            // default send scenario
            scope.requestPipeline.intercept(HttpRequestPipeline.Send) { content ->
                check(content is OutgoingContent) {
                    """
|Fail to serialize body. Content has type: ${content::class}, but OutgoingContent expected.
|If you expect serialized body, please check that you have installed the corresponding plugin(like `ContentNegotiation`) and set `Content-Type` header."""
                        .trimMargin()
                }
                context.setBody(content)

                val realSender: Sender = DefaultSender(plugin.maxSendCount, scope)
                var interceptedSender = realSender
                (plugin.interceptors.lastIndex downTo 0).forEach {
                    val interceptor = plugin.interceptors[it]
                    interceptedSender = InterceptedSender(interceptor, interceptedSender)
                }
                val call = interceptedSender.execute(context)
                proceedWith(call)
            }
        }
    }

    private class InterceptedSender(
        private val interceptor: HttpSendInterceptor,
        private val nextSender: Sender
    ) : Sender {

        override suspend fun execute(requestBuilder: HttpRequestBuilder): HttpClientCall {
            return interceptor.invoke(nextSender, requestBuilder)
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
