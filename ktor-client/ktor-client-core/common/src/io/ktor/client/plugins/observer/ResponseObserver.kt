/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.observer

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * [ResponseObserver] callback.
 */
public typealias ResponseHandler = suspend (HttpResponse) -> Unit

/**
 * Observe response plugin.
 */
public class ResponseObserver(
    private val responseHandler: ResponseHandler,
    private val filter: ((HttpClientCall) -> Boolean)? = null
) {
    @KtorDsl
    public class Config {
        internal var responseHandler: ResponseHandler = {}

        internal var filter: ((HttpClientCall) -> Boolean)? = null

        /**
         * Set response handler for logging.
         */
        public fun onResponse(block: ResponseHandler) {
            responseHandler = block
        }

        /**
         * Set filter predicate to dynamically control if interceptor is executed or not for each http call.
         */
        public fun filter(block: ((HttpClientCall) -> Boolean)) {
            filter = block
        }
    }

    public companion object Plugin : HttpClientPlugin<Config, ResponseObserver> {

        override val key: AttributeKey<ResponseObserver> = AttributeKey("BodyInterceptor")

        override fun prepare(block: Config.() -> Unit): ResponseObserver {
            val config = Config().apply(block)
            return ResponseObserver(config.responseHandler, config.filter)
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: ResponseObserver, scope: HttpClient) {
            scope.receivePipeline.intercept(HttpReceivePipeline.After) { response ->
                if (plugin.filter?.invoke(response.call) == false) return@intercept

                val (loggingContent, responseContent) = response.content.split(response)

                val newResponse = response.wrapWithContent(responseContent)
                val sideResponse = response.call.wrapWithContent(loggingContent).response

                scope.launch {
                    try {
                        plugin.responseHandler(sideResponse)
                    } catch (_: Throwable) {
                    }

                    val content = sideResponse.content
                    if (!content.isClosedForRead) {
                        content.discard()
                    }
                }

                proceedWith(newResponse)
            }
        }
    }
}

/**
 * Install [ResponseObserver] plugin in client.
 */
@Suppress("FunctionName")
public fun HttpClientConfig<*>.ResponseObserver(block: ResponseHandler) {
    install(ResponseObserver) {
        responseHandler = block
    }
}
