/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.HttpCallValidator")

/**
 * [HttpCallValidator] configuration.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.HttpCallValidatorConfig)
 */
@KtorDsl
public class HttpCallValidatorConfig {
    internal val responseValidators: MutableList<ResponseValidator> = mutableListOf()
    internal val responseExceptionHandlers: MutableList<HandlerWrapper> = mutableListOf()

    /**
     * Terminate [HttpClient.receivePipeline] if status code is not successful (>=300).
     */
    internal var expectSuccess: Boolean = true

    /**
     * Add [CallRequestExceptionHandler].
     * Last added handler executes first.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.HttpCallValidatorConfig.handleResponseException)
     */
    public fun handleResponseException(block: CallRequestExceptionHandler) {
        responseExceptionHandlers += RequestExceptionHandlerWrapper(block)
    }

    /**
     * Add [CallRequestExceptionHandler].
     * Last added handler executes first.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.HttpCallValidatorConfig.handleResponseExceptionWithRequest)
     */
    public fun handleResponseExceptionWithRequest(block: CallRequestExceptionHandler) {
        responseExceptionHandlers += RequestExceptionHandlerWrapper(block)
    }

    /**
     * Add [ResponseValidator].
     * Last added validator executes first.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.HttpCallValidatorConfig.validateResponse)
     */
    public fun validateResponse(block: ResponseValidator) {
        responseValidators += block
    }
}

/**
 * Response validator method.
 *
 * You could throw an exception to fail the response.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.ResponseValidator)
 */
public typealias ResponseValidator = suspend (response: HttpResponse) -> Unit

/**
 * Response exception handler method.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.CallExceptionHandler)
 */
public typealias CallExceptionHandler = suspend (cause: Throwable) -> Unit

/**
 * Response exception handler method. [request] is null if
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.CallRequestExceptionHandler)
 */
public typealias CallRequestExceptionHandler = suspend (cause: Throwable, request: HttpRequest) -> Unit

/**
 * The response validator plugin is used for validating an [HttpClient] response and handling response exceptions.
 *
 * For more details, see [HttpCallValidatorConfig].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.HttpCallValidator)
 */
public val HttpCallValidator: ClientPlugin<HttpCallValidatorConfig> = createClientPlugin(
    "HttpResponseValidator",
    ::HttpCallValidatorConfig
) {

    val responseValidators: List<ResponseValidator> = pluginConfig.responseValidators.reversed()
    val callExceptionHandlers: List<HandlerWrapper> = pluginConfig.responseExceptionHandlers.reversed()

    val expectSuccess: Boolean = pluginConfig.expectSuccess

    suspend fun validateResponse(response: HttpResponse) {
        LOGGER.trace("Validating response for request ${response.call.request.url}")
        responseValidators.forEach { it(response) }
    }

    suspend fun processException(cause: Throwable, request: HttpRequest) {
        LOGGER.trace("Processing exception $cause for request ${request.url}")
        callExceptionHandlers.forEach {
            when (it) {
                is ExceptionHandlerWrapper -> it.handler(cause)
                is RequestExceptionHandlerWrapper -> it.handler(cause, request)
            }
        }
    }

    on(SetupRequest) { request ->
        request.attributes.computeIfAbsent(ExpectSuccessAttributeKey) { expectSuccess }
    }

    on(Send) { request ->
        val call = proceed(request)
        validateResponse(call.response)
        call
    }

    on(RequestError) { request, cause ->
        val unwrappedCause = cause.unwrapCancellationException()
        processException(unwrappedCause, request)
        unwrappedCause
    }

    on(ReceiveError) { request, cause ->
        val unwrappedCause = cause.unwrapCancellationException()
        processException(unwrappedCause, request)
        unwrappedCause
    }
}

internal object RequestError : ClientHook<suspend (HttpRequest, Throwable) -> Throwable?> {
    override fun install(client: HttpClient, handler: suspend (HttpRequest, Throwable) -> Throwable?) {
        client.requestPipeline.intercept(HttpRequestPipeline.Before) {
            try {
                proceed()
            } catch (cause: Throwable) {
                val error = handler(HttpRequest(context), cause)
                if (error != null) throw error
            }
        }
    }
}

internal object ReceiveError : ClientHook<suspend (HttpRequest, Throwable) -> Throwable?> {
    override fun install(client: HttpClient, handler: suspend (HttpRequest, Throwable) -> Throwable?) {
        val BeforeReceive = PipelinePhase("BeforeReceive")
        client.responsePipeline.insertPhaseBefore(HttpResponsePipeline.Receive, BeforeReceive)
        client.responsePipeline.intercept(BeforeReceive) {
            try {
                proceed()
            } catch (cause: Throwable) {
                val error = handler(context.request, cause)
                if (error != null) throw error
            }
        }
    }
}

private fun HttpRequest(builder: HttpRequestBuilder): HttpRequest = object : HttpRequest {
    override val call: HttpClientCall get() = error("Call is not initialized")
    override val method: HttpMethod = builder.method
    override val url: Url = builder.url.build()
    override val attributes: Attributes = builder.attributes
    override val headers: Headers = builder.headers.build()
    override val content: OutgoingContent
        get() = builder.body as? OutgoingContent
            ?: error("Content was not transformed to OutgoingContent yet. Current body is ${builder.body}")
}

/**
 * Install [HttpCallValidator] with [block] configuration.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.HttpResponseValidator)
 */
@Suppress("FunctionName")
public fun HttpClientConfig<*>.HttpResponseValidator(block: HttpCallValidatorConfig.() -> Unit) {
    install(HttpCallValidator, block)
}

/**
 * Terminate [HttpClient.receivePipeline] if status code is not successful (>=300).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.expectSuccess)
 */
public var HttpRequestBuilder.expectSuccess: Boolean
    get() = attributes.getOrNull(ExpectSuccessAttributeKey) ?: true
    set(value) = attributes.put(ExpectSuccessAttributeKey, value)

internal val ExpectSuccessAttributeKey = AttributeKey<Boolean>("ExpectSuccessAttributeKey")

internal sealed interface HandlerWrapper

internal class ExceptionHandlerWrapper(val handler: CallExceptionHandler) : HandlerWrapper

internal class RequestExceptionHandlerWrapper(val handler: CallRequestExceptionHandler) : HandlerWrapper
