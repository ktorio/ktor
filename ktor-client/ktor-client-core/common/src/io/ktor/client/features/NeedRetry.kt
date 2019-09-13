/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.takeFrom
import io.ktor.client.response.HttpReceivePipeline
import io.ktor.client.response.HttpResponse
import io.ktor.util.AttributeKey

/**
 * Need retry condition checker
 *
 * You could return true to make [HttpClient] retry the request
 */
typealias RetryCondition = suspend (requestBuilder: HttpRequestBuilder, response: HttpResponse) -> Boolean

/**
 * Need retry feature for [HttpClient]
 *
 * @property retryHandlers: list of retry conditions to check before deciding to retry a request
 */
class NeedRetry(
    private val retryHandlers: List<RetryCondition>
) {

    /**
     * [NeedRetry] configuration.
     */
    class Config {
        internal val retryHandlers: MutableList<RetryCondition> = mutableListOf()

        /**
         * Add [RetryCondition].
         * Last added handler executes first.
         */
        fun retryCondition(block: RetryCondition) {
            retryHandlers += block
        }
    }

    companion object : HttpClientFeature<Config, NeedRetry> {
        override val key: AttributeKey<NeedRetry> = AttributeKey("NeedRetry")

        override fun prepare(block: Config.() -> Unit): NeedRetry {
            val config = Config().apply(block)

            config.retryHandlers.reversed()

            return NeedRetry(config.retryHandlers)
        }

        override fun install(feature: NeedRetry, scope: HttpClient) {
            scope.receivePipeline.intercept(HttpReceivePipeline.After) {
                try {
                    val requestBuilder = HttpRequestBuilder().takeFrom(context.request)
                    val isRetryNeeded = feature.retryHandlers
                        .map { it(requestBuilder, context.response) }
                        .contains(true)

                    if (isRetryNeeded) {
                        context.client.execute(requestBuilder)
                    }

                    proceedWith(it)
                } catch (cause: Throwable) {
                    throw cause
                }
            }
        }
    }
}

/**
 * Install [NeedRetry] with [block] configuration.
 */
fun HttpClientConfig<*>.RetryCondition(block: NeedRetry.Config.() -> Unit) {
    install(NeedRetry, block)
}
