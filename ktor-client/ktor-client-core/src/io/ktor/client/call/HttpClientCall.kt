package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.content.*
import java.io.*
import java.util.concurrent.atomic.*
import kotlin.reflect.*
import kotlin.reflect.full.*

/**
 * A class that represents a single pair of [request] and [response] for a specific [HttpClient].
 */
class HttpClientCall private constructor(
        private val client: HttpClient
) : Closeable {
    private val received = AtomicBoolean(false)

    /**
     * Represents the [request] sent by the client.
     */
    lateinit var request: HttpRequest
        private set

    /**
     * Represents the [response] sent by the server.
     */
    lateinit var response: HttpResponse
        private set

    /**
     * Tries to receive the payload of the [response] as an specific [expectedType].
     * Returns [response] if [expectedType] is [HttpResponse].
     *
     * @throws NoTransformationFound If no transformation is found for the [expectedType].
     * @throws DoubleReceiveException If already called [receive].
     */
    suspend fun receive(expectedType: KClass<*>): Any {
        if (expectedType.isInstance(response)) return response
        if (!received.compareAndSet(false, true)) throw DoubleReceiveException(this)

        val subject = HttpResponseContainer(expectedType, response)
        val result = client.responsePipeline.execute(this, subject).response

        if (!expectedType.isInstance(result)) throw NoTransformationFound(result::class, expectedType)
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
         * Creates a new [HttpClientCall] building the request with the specified [requestBuilder]
         * and using a specific HTTP [client].
         */
        suspend fun create(requestBuilder: HttpRequestBuilder, client: HttpClient): HttpClientCall {
            val call = HttpClientCall(client)

            val received = client.requestPipeline.execute(requestBuilder, requestBuilder.body)
            val content = received as? OutgoingContent
                    ?: throw NoTransformationFound(received::class, OutgoingContent::class)

            requestBuilder.body = received

            val requestData = requestBuilder.build()

            call.request = client.createRequest(requestData, call)
            call.response = call.request.execute()
            return call
        }
    }
}

/**
 * Constructs a [HttpClientCall] from this [HttpClient] and with the specified [HttpRequestBuilder]
 * configured inside the [block].
 */
suspend fun HttpClient.call(block: HttpRequestBuilder.() -> Unit = {}): HttpClientCall =
        HttpClientCall.create(HttpRequestBuilder().apply(block), this)

/**
 * Tries to receive the payload of the [response] as an specific type [T].
 *
 * @throws NoTransformationFound If no transformation is found for the type [T].
 * @throws DoubleReceiveException If already called [receive].
 */
suspend inline fun <reified T> HttpClientCall.receive(): T = receive(T::class) as T

/**
 * Exception representing that the response payload has already been received.
 */
class DoubleReceiveException(call: HttpClientCall) : IllegalStateException() {
    override val message: String = "Request already received: $call"
}

/**
 * Exception representing the no transformation was found.
 * It includes the received type and the expected type as part of the message.
 */
class NoTransformationFound(from: KClass<*>, to: KClass<*>) : UnsupportedOperationException() {
    override val message: String? = "No transformation found: $from -> $to"
}
