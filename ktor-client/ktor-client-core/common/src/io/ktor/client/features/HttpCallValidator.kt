/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*

/**
 * Response validator method.
 *
 * You could throw an exception to fail the response.
 */
public typealias ResponseValidator = suspend (response: HttpResponse) -> Unit

/**
 * Response exception handler method.
 */
public typealias CallExceptionHandler = suspend (cause: Throwable) -> Unit

/**
 * Response validator feature is used for validate response and handle response exceptions.
 *
 * See also [Config] for additional details.
 */
public class HttpCallValidator(
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
    public class Config {
        internal val responseValidators: MutableList<ResponseValidator> = mutableListOf()
        internal val responseExceptionHandlers: MutableList<CallExceptionHandler> = mutableListOf()

        /**
         * Add [CallExceptionHandler].
         * Last added handler executes first.
         */
        public fun handleResponseException(block: CallExceptionHandler) {
            responseExceptionHandlers += block
        }

        /**
         * Add [ResponseValidator].
         * Last added validator executes first.
         */
        public fun validateResponse(block: ResponseValidator) {
            responseValidators += block
        }
    }

    public companion object : HttpClientFeature<Config, HttpCallValidator> {
        override val key: AttributeKey<HttpCallValidator> = AttributeKey("HttpResponseValidator")

        override fun prepare(block: Config.() -> Unit): HttpCallValidator {
            val config = Config().apply(block)

            return HttpCallValidator(
                config.responseValidators.reversed(),
                config.responseExceptionHandlers.reversed()
            )
        }

        override fun install(feature: HttpCallValidator, scope: HttpClient) {
            val BeforeReceive = PipelinePhase("BeforeReceive")
            scope.responsePipeline.insertPhaseBefore(HttpResponsePipeline.Receive, BeforeReceive)

            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                try {
                    proceedWith(it)
                } catch (cause: Throwable) {
                    val unwrappedCause = cause.unwrapCancellationException()
                    feature.processException(unwrappedCause)
                    throw unwrappedCause
                }
            }

            scope.responsePipeline.intercept(BeforeReceive) { container ->
                try {
                    feature.validateResponse(context.response)
                    proceedWith(container)
                } catch (cause: Throwable) {
                    val unwrappedCause = cause.unwrapCancellationException()
                    feature.processException(unwrappedCause)
                    throw unwrappedCause
                }
            }
        }
    }
}

/**
 * Install [HttpCallValidator] with [block] configuration.
 */
public fun HttpClientConfig<*>.HttpResponseValidator(block: HttpCallValidator.Config.() -> Unit) {
    install(HttpCallValidator, block)
}
