package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.io.core.*
import kotlin.coroutines.*
import kotlin.reflect.*

/**
 * A class that represents a single pair of [request] and [response] for a specific [HttpClient].
 *
 * @property client: client that executed the call.
 */
open class HttpClientCall constructor(
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
    val responseConfig: HttpResponseConfig = client.engineConfig.response

    /**
     * Tries to receive the payload of the [response] as an specific [expectedType].
     * Returns [response] if [expectedType] is [HttpResponse].
     *
     * @throws NoTransformationFoundException If no transformation is found for the [expectedType].
     * @throws DoubleReceiveException If already called [receive].
     */
    suspend fun receive(info: TypeInfo): Any {
        if (info.type.isInstance(response)) return response
        if (!received.compareAndSet(false, true)) throw DoubleReceiveException(this)

        val subject = HttpResponseContainer(info, response)
        try {
            val result = client.responsePipeline.execute(this, subject).response
            if (!info.type.isInstance(result)) throw NoTransformationFoundException(result::class, info.type)
            return result
        } catch (cause: BadResponseStatusException) {
            throw cause
        } catch (cause: Throwable) {
            throw ReceivePipelineException(response.call, info, cause)
        }
    }

    /**
     * Closes the underlying [response].
     */
    override fun close() {
        response.close()
    }
}

/**
 * Raw http call produced by engine.
 *
 * @property request - executed http request.
 * @property response - raw http response
 */
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
) : IllegalStateException("Fail to run receive pipeline")

/**
 * Exception representing the no transformation was found.
 * It includes the received type and the expected type as part of the message.
 */
@Suppress("KDocMissingDocumentation")
class NoTransformationFoundException(from: KClass<*>, to: KClass<*>) : UnsupportedOperationException() {
    override val message: String? = "No transformation found: $from -> $to"
}

@Deprecated(
    "[NoTransformationFound] is deprecated. Use [NoTransformationFoundException] instead",
    ReplaceWith("NoTransformationFoundException"),
    DeprecationLevel.ERROR
)
@Suppress("KDocMissingDocumentation")
typealias NoTransformationFound = NoTransformationFoundException
