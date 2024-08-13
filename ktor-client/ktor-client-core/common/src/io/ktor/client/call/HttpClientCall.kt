/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.coroutines.*
import kotlin.reflect.*

/**
 * A pair of a [request] and [response] for a specific [HttpClient].
 *
 * @property client the client that executed the call.
 */
public open class HttpClientCall(
    public val client: HttpClient
) : CoroutineScope {
    override val coroutineContext: CoroutineContext get() = response.coroutineContext

    /**
     * Typed [Attributes] associated to this call serving as a lightweight container.
     */
    public val attributes: Attributes get() = request.attributes

    /**
     * The [request] sent by the client.
     */
    public lateinit var request: HttpRequest
        protected set

    /**
     * The [response] sent by the server.
     */
    public lateinit var response: HttpResponse
        protected set

    @InternalAPI
    public constructor(
        client: HttpClient,
        requestData: HttpRequestData,
        responseData: HttpResponseData
    ) : this(client) {
        this.request = DefaultHttpRequest(this, requestData)
        this.response = DefaultHttpResponse(this, responseData)

        if (responseData.body !is ByteReadChannel) {
            attributes.put(CustomResponse, responseData.body)
        }
    }

    /**
     * Tries to receive the payload of the [response] as a specific expected type provided in [info].
     * Returns [response] if [info] corresponds to [HttpResponse].
     *
     * @throws NoTransformationFoundException If no transformation is found for the type [info].
     * @throws DoubleReceiveException If already called [body].
     */
    @OptIn(InternalAPI::class)
    public suspend fun bodyNullable(info: TypeInfo): Any? {
        try {
            if (response.instanceOf(info.type)) return response

            val transformBody: suspend (Any) -> Any? = { responseData ->
                if (responseData is ByteReadChannel && responseData.closedCause != null) {
                    throw responseData.closedCause!!
                }
                val subject = HttpResponseContainer(info, responseData)
                client.responsePipeline.execute(this, subject).response
                    .takeIf { it != NullBody }
                    .also {
                        if (it != null && !it.instanceOf(info.type)) {
                            val from = it::class
                            val to = info.type
                            throw NoTransformationFoundException(response, from, to)
                        }
                    }
            }
            return attributes.getOrNull(CustomResponse)
                ?.let { transformBody(it) }
                ?: response.body.read(
                    consume = !info.isStreamingType(),
                    operation = transformBody
                )
        } catch (cause: Throwable) {
            response.cancel("Receive failed", cause)
            throw cause
        }
    }

    /**
     * We do not close the body after reading when providing streaming data.
     */
    private fun TypeInfo.isStreamingType() =
        type in setOf(Source::class, ByteReadChannel::class) || type.qualifiedName?.contains("java.io") == true

    /**
     * Tries to receive the payload of the [response] as a specific expected type provided in [info].
     * Returns [response] if [info] corresponds to [HttpResponse].
     *
     * @throws NoTransformationFoundException If no transformation is found for the type [info].
     * @throws DoubleReceiveException If already called [body].
     * @throws NullPointerException If content is `null`.
     */
    @OptIn(InternalAPI::class)
    public suspend fun body(info: TypeInfo): Any = bodyNullable(info)!!

    override fun toString(): String = "HttpClientCall[${request.url}, ${response.status}]"

    internal fun setResponse(response: HttpResponse) {
        this.response = response
    }

    internal fun setRequest(request: HttpRequest) {
        this.request = request
    }

    public companion object {
        private val CustomResponse: AttributeKey<Any> = AttributeKey("CustomResponse")
    }
}

/**
 * Tries to receive the payload of the [response] as a specific type [T].
 *
 * @throws NoTransformationFoundException If no transformation is found for the type [T].
 * @throws DoubleReceiveException If already called [body].
 */
public suspend inline fun <reified T> HttpClientCall.body(): T = bodyNullable(typeInfo<T>()) as T

/**
 * Tries to receive the payload of the [response] as a specific type [T].
 *
 * @throws NoTransformationFoundException If no transformation is found for the type [T].
 * @throws DoubleReceiveException If already called [body].
 */
public suspend inline fun <reified T> HttpResponse.body(): T = call.bodyNullable(typeInfo<T>()) as T

/**
 * Tries to receive the payload of the [response] as a specific type [T] described in [typeInfo].
 *
 * @throws NoTransformationFoundException If no transformation is found for the type info [typeInfo].
 * @throws DoubleReceiveException If already called [body].
 */
@Suppress("UNCHECKED_CAST")
public suspend fun <T> HttpResponse.body(typeInfo: TypeInfo): T = call.bodyNullable(typeInfo) as T

/**
 * Exception representing that the response payload has already been received.
 */
public class DoubleReceiveException(initialReceive: InitialReceiveEvent) : IllegalStateException(initialReceive) {
    override val message: String = "Response already received; see cause stack for previous invocation"
}

public class InitialReceiveEvent: Throwable()

/**
 * Exception representing fail of the response pipeline
 * [cause] contains origin pipeline exception
 */
@Suppress("KDocMissingDocumentation", "unused")
public class ReceivePipelineException(
    public val request: HttpClientCall,
    public val info: TypeInfo,
    override val cause: Throwable
) : IllegalStateException("Fail to run receive pipeline: $cause")

/**
 * Exception represents the inability to find a suitable transformation for the received body from
 * the resulted type to the expected by the client type.
 *
 * You can read how to resolve NoTransformationFoundException at [FAQ](https://ktor.io/docs/faq.html#no-transformation-found-exception)
 */
public class NoTransformationFoundException(
    response: HttpResponse,
    from: KClass<*>,
    to: KClass<*>
) : UnsupportedOperationException() {
    override val message: String = """
        Expected response body of the type '$to' but was '$from'
        In response from `${response.request.url}`
        Response status `${response.status}`
        Response header `ContentType: ${response.headers[HttpHeaders.ContentType]}` 
        Request header `Accept: ${response.request.headers[HttpHeaders.Accept]}`
        
        You can read how to resolve NoTransformationFoundException at FAQ: 
        https://ktor.io/docs/faq.html#no-transformation-found-exception
    """.trimIndent()
}
