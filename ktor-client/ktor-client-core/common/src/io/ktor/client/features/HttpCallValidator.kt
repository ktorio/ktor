/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

/**
 * Response validator method.
 *
 * You could throw an exception to fail the response.
 */
typealias ResponseValidator = suspend (response: HttpResponse) -> Unit

/**
 * Response exception handler method.
 */
typealias CallExceptionHandler = suspend (cause: Throwable) -> Unit

/**
 * Response validator feature is used for validate response and handle response exceptions.
 *
 * See also [Config] for additional details.
 */
class HttpCallValidator(
    private val responseValidators: List<ResponseValidator>,
    private val callExceptionHandlers: List<CallExceptionHandler>
) {
    private suspend fun validateResponse(response: HttpResponse) {
        responseValidators.forEach { it(response) }
    }

    private suspend fun processException(cause: Throwable) {
        callExceptionHandlers.forEach { it(cause) }
    }

    /**
     * [HttpCallValidator] configuration.
     */
    class Config {
        internal val responseValidators: MutableList<ResponseValidator> = mutableListOf()
        internal val responseExceptionHandlers: MutableList<CallExceptionHandler> = mutableListOf()

        /**
         * Add [CallExceptionHandler].
         * Last added handler executes first.
         */
        fun handleResponseException(block: CallExceptionHandler) {
            responseExceptionHandlers += block
        }

        /**
         * Add [ResponseValidator].
         * Last added validator executes first.
         */
        fun validateResponse(block: ResponseValidator) {
            responseValidators += block
        }
    }

    companion object : HttpClientFeature<Config, HttpCallValidator> {
        override val key: AttributeKey<HttpCallValidator> = AttributeKey("HttpResponseValidator")

        override fun prepare(block: Config.() -> Unit): HttpCallValidator {
            val config = Config().apply(block)

            config.responseValidators.reversed()
            config.responseExceptionHandlers.reversed()

            return HttpCallValidator(
                config.responseValidators,
                config.responseExceptionHandlers
            )
        }

        override fun install(feature: HttpCallValidator, scope: HttpClient) {
            val BeforeReceive = PipelinePhase("BeforeReceive")
            scope.responsePipeline.insertPhaseBefore(HttpResponsePipeline.Receive, BeforeReceive)

            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                try {
                    proceedWith(it)
                } catch (cause: Throwable) {
                    feature.processException(cause)
                    throw cause
                }
            }

            scope.responsePipeline.intercept(BeforeReceive) { container ->
                try {
                    feature.validateResponse(context.response)
                    proceedWith(container)
                } catch (cause: Throwable) {
                    feature.processException(cause)
                    throw cause
                }
            }
        }
    }
}

/**
 * Install [HttpCallValidator] with [block] configuration.
 */
fun HttpClientConfig<*>.HttpResponseValidator(block: HttpCallValidator.Config.() -> Unit) {
    install(HttpCallValidator, block)
}
