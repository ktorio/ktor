package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import java.io.*
import java.net.*
import java.util.concurrent.atomic.*
import kotlin.reflect.*


class HttpClientCall(
        val request: HttpRequest,
        responseBuilder: HttpResponseBuilder,
        private val scope: HttpClient
) : Closeable {
    val response: HttpResponse = responseBuilder.build(this)

    private val received: AtomicBoolean = AtomicBoolean(false)

    suspend fun receive(expectedType: KClass<*> = Unit::class): HttpResponseContainer {
        if (received.getAndSet(true)) throw DoubleReceiveException(this)
        val subject = HttpResponseContainer(expectedType, request, HttpResponseBuilder(response))
        val container = scope.responsePipeline.execute(scope, subject)

        val value = container.response.body::class == expectedType || HttpResponse::class == expectedType
        assert(value, { "Expected to receive: $expectedType, but received: ${container.response.body::class}" })
        return container
    }

    override fun close() {
        response.close()
    }
}

suspend fun HttpClient.call(block: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
    val call = requestPipeline.execute(this, HttpRequestBuilder().apply(block))
    return (call as? HttpClientCall)?.response ?: error("Http response invalid: $call")
}

suspend fun HttpClient.call(builder: HttpRequestBuilder): HttpResponse = call({ takeFrom(builder) })

suspend fun HttpClient.call(url: URL, block: HttpRequestBuilder.() -> Unit = {}): HttpResponse = call {
    this.url.takeFrom(url)
    block()
}

suspend fun HttpClient.call(url: String, block: HttpRequestBuilder.() -> Unit = {}): HttpResponse =
        call(URL(decodeURLPart(url)), block)

suspend inline fun <reified T> HttpResponse.receive(): T {
    if (T::class === HttpResponse::class) return this as T

    val body = call.receive(T::class).response.body
    return body as? T ?: throw NoTransformationFound(body::class, T::class)
}

class DoubleReceiveException(call: HttpClientCall) : IllegalStateException() {
    override val message: String = "Request already received: $call"
}

class NoTransformationFound(from: KClass<*>, to: KClass<*>) : UnsupportedOperationException() {
    override val message: String? = "No transformation found: $from -> $to"
}
