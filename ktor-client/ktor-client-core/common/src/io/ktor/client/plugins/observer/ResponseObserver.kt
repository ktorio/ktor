/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.observer

import io.ktor.client.*
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
    private val responseHandler: ResponseHandler
) {
    public class Config {
        internal var responseHandler: ResponseHandler = {}

        /**
         * Set response handler for logging.
         */
        public fun onResponse(block: ResponseHandler) {
            responseHandler = block
        }
    }

    public companion object Plugin : HttpClientPlugin<Config, ResponseObserver> {

        override val key: AttributeKey<ResponseObserver> = AttributeKey("BodyInterceptor")

        override fun prepare(block: Config.() -> Unit): ResponseObserver =
            ResponseObserver(Config().apply(block).responseHandler)

        override fun install(plugin: ResponseObserver, scope: HttpClient) {
            scope.receivePipeline.intercept(HttpReceivePipeline.After) { response ->
                val (loggingContent, responseContent) = response.content.split(response)

                val newClientCall = context.wrapWithContent(responseContent)
                val sideCall = newClientCall.wrapWithContent(loggingContent)

                scope.launch {
                    try {
                        plugin.responseHandler(sideCall.response)
                    } catch (_: Throwable) {
                    }

                    val content = sideCall.response.content
                    if (!content.isClosedForRead) {
                        content.discard()
                    }
                }

                context.response = newClientCall.response
                context.request = newClientCall.request
                proceedWith(context.response)
            }
        }
    }
}

/**
 * Install [ResponseObserver] plugin in client.
 */
public fun HttpClientConfig<*>.ResponseObserver(block: ResponseHandler) {
    install(ResponseObserver) {
        responseHandler = block
    }
}
