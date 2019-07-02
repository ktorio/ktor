/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.coroutines.*
import kotlin.reflect.*

@InternalAPI
internal fun HttpClientCall(
    client: HttpClient,
    requestData: HttpRequestData,
    responseData: HttpResponseData
): HttpClientCall = HttpClientCall(client).apply {
    request = DefaultHttpRequest(this, requestData)
    response = DefaultHttpResponse(this, responseData)

    if (responseData.body !is ByteReadChannel) {
        attributes.put(HttpClientCall.CustomResponse, responseData.body)
    }
}

/**
 * A class that represents a single pair of [request] and [response] for a specific [HttpClient].
 *
 * @property client: client that executed the call.
 */
open class HttpClientCall internal constructor(
    val client: HttpClient
) : CoroutineScope, Closeable {
    private val received = atomic(false)

    override val coroutineContext: CoroutineContext get() = response.coroutineContext

    /**
     * Typed [Attributes] associated to this call serving as a lightweight container.
     */
    val attributes: Attributes get() = request.attributes

    /**
     * Represents the [request] sent by the client
     */
    lateinit var request: HttpRequest
        internal set

    /**
     * Represents the [response] sent by the server.
     */
    lateinit var response: HttpResponse
        internal set

    /**
     * Configuration for the [response].
     */
    @Deprecated(
        message = "responseConfig is deprecated. Consider using [Charsets] config instead",
        level = DeprecationLevel.ERROR
    )
    val responseConfig: HttpResponseConfig = client.engineConfig.response

    /**
     * Tries to receive the payload of the [response] as an specific [expectedType].
     * Returns [response] if [expectedType] is [HttpResponse].
     *
     * @throws NoTransformationFoundException If no transformation is found for the [expectedType].
     * @throws DoubleReceiveException If already called [receive].
     */
    suspend fun receive(info: TypeInfo): Any {
        if (response.instanceOf(info.type)) return response
        if (!received.compareAndSet(false, true)) throw DoubleReceiveException(this)

        val responseData = attributes.getOrNull(CustomResponse) ?: response.content

        val subject = HttpResponseContainer(info, responseData)
        val result = client.responsePipeline.execute(this, subject).response
        if (!result.instanceOf(info.type)) {
            val from = result::class
            val to = info.type
            throw NoTransformationFoundException(from, to)
        }

        if (result is ByteReadChannel) {
            return response.channelWithCloseHandling()
        }

        if (result !is Closeable && result !is HttpRequest) {
            close()
        }

        return result
    }

    /**
     * Closes the underlying [response].
     */
    override fun close() {
        response.close()
    }

    companion object {
        /**
         * [CustomResponse] key used to process the response of custom type in case of [HttpClientEngine] can't return body bytes directly.
         * If present, attribute value will be an initial value for [HttpResponseContainer] in [HttpClient.responsePipeline].
         *
         * Example: [WebSocketSession]
         */
        @KtorExperimentalAPI
        val CustomResponse: AttributeKey<Any> = AttributeKey<Any>("CustomResponse")
    }
}

/**
 * Raw http call produced by engine.
 *
 * @property request - executed http request.
 * @property response - raw http response
 */
@Deprecated(
    "HttpEngineCall deprecated.",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpResponseData")
)
@InternalAPI
data class HttpEngineCall(val request: HttpRequest, val response: HttpResponse)

/**
 * Constructs a [HttpClientCall] from this [HttpClient] and with the specified [HttpRequestBuilder]
 * configured inside the [block].
 */
suspend fun HttpClient.call(block: suspend HttpRequestBuilder.() -> Unit = {}): HttpClientCall =
    execute(HttpRequestBuilder().apply { block() })

/**
 * Tries to receive the payload of the [response] as an specific type [T].
 *
 * @throws NoTransformationFoundException If no transformation is found for the type [T].
 * @throws DoubleReceiveException If already called [receive].
 */
suspend inline fun <reified T> HttpClientCall.receive(): T = receive(typeInfo<T>()) as T

/**
 * Tries to receive the payload of the [response] as an specific type [T].
 *
 * @throws NoTransformationFoundException If no transformation is found for the type [T].
 * @throws DoubleReceiveException If already called [receive].
 */
suspend inline fun <reified T> HttpResponse.receive(): T = call.receive(typeInfo<T>()) as T

/**
 * Exception representing that the response payload has already been received.
 */
@Suppress("KDocMissingDocumentation")
class DoubleReceiveException(call: HttpClientCall) : IllegalStateException() {
    override val message: String = "Response already received: $call"
}

/**
 * Exception representing fail of the response pipeline
 * [cause] contains origin pipeline exception
 */
@Suppress("KDocMissingDocumentation")
class ReceivePipelineException(
    val request: HttpClientCall,
    val info: TypeInfo,
    override val cause: Throwable
) : IllegalStateException("Fail to run receive pipeline: $cause")

/**
 * Exception representing the no transformation was found.
 * It includes the received type and the expected type as part of the message.
 */
@Suppress("KDocMissingDocumentation")
class NoTransformationFoundException(from: KClass<*>, to: KClass<*>) : UnsupportedOperationException() {
    override val message: String? = "No transformation found: $from -> $to"
}
