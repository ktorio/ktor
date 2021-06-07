/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.features.HttpCallValidator.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlin.native.concurrent.*

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
public class HttpCallValidator internal constructor(
    private val responseValidators: List<ResponseValidator>,
    private val callExceptionHandlers: List<CallExceptionHandler>,
    private val expectSuccess: Boolean
) {

    /**
     * Response validator feature is used for validate response and handle response exceptions.
     *
     * See also [Config] for additional details.
     */
    @Deprecated(
        "This is going to become internal. " +
            "Please file a ticket and clarify, why do you need it."
    )
    public constructor(
        responseValidators: List<ResponseValidator>,
        callExceptionHandlers: List<CallExceptionHandler>
    ) : this(responseValidators, callExceptionHandlers, true)

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
         * Terminate [HttpClient.receivePipeline] if status code is not successful (>=300).
         */

        @Deprecated(
            "This property is ignored. Please use `expectSuccess` property in HttpClientConfig. " +
                "This is going to become internal."
        )
        public var expectSuccess: Boolean = true

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
                config.responseExceptionHandlers.reversed(),
                config.expectSuccess
            )
        }

        override fun install(feature: HttpCallValidator, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                try {
                    context.attributes.computeIfAbsent(ExpectSuccessAttributeKey) { feature.expectSuccess }
                    proceedWith(it)
                } catch (cause: Throwable) {
                    val unwrappedCause = cause.unwrapCancellationException()
                    feature.processException(unwrappedCause)
                    throw unwrappedCause
                }
            }

            val BeforeReceive = PipelinePhase("BeforeReceive")
            scope.responsePipeline.insertPhaseBefore(HttpResponsePipeline.Receive, BeforeReceive)
            scope.responsePipeline.intercept(BeforeReceive) { container ->
                try {
                    proceedWith(container)
                } catch (cause: Throwable) {
                    val unwrappedCause = cause.unwrapCancellationException()
                    feature.processException(unwrappedCause)
                    throw unwrappedCause
                }
            }

            scope[HttpSend].intercept { call, _ ->
                feature.validateResponse(call.response)
                call
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

/**
 * Terminate [HttpClient.receivePipeline] if status code is not successful (>=300).
 */
public var HttpRequestBuilder.expectSuccess: Boolean
    get() = attributes.getOrNull(ExpectSuccessAttributeKey) ?: true
    set(value) = attributes.put(ExpectSuccessAttributeKey, value)

@SharedImmutable
internal val ExpectSuccessAttributeKey = AttributeKey<Boolean>("ExpectSuccessAttributeKey")
