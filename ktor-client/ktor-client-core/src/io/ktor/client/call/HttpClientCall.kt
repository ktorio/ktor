package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import java.net.*
import kotlin.reflect.*


class HttpClientCall(
        val request: HttpRequest,
        responseBuilder: HttpResponseBuilder,
        private val scope: HttpClient
) {
    val response: HttpResponse = responseBuilder.build(this)

    suspend fun receive(expectedType: KClass<*> = Unit::class): HttpResponseContainer {
        val subject = HttpResponseContainer(expectedType, request, HttpResponseBuilder(response))
        val container = scope.responsePipeline.execute(scope, subject)

        assert(container.response.payload::class === expectedType || HttpResponse::class === expectedType)
        return container
    }
}

fun HttpClientCall.close() = response.close()

suspend fun HttpClient.call(builder: HttpRequestBuilder): HttpResponse {
    val call = requestPipeline.execute(this, HttpRequestBuilder().takeFrom(builder))
    return (call as? HttpClientCall)?.response ?: error("Http response invalid: $call")
}

suspend fun HttpClient.call(block: HttpRequestBuilder.() -> Unit = {}): HttpResponse =
        call(HttpRequestBuilder().apply(block))

suspend fun HttpClient.call(url: URL, block: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
    val builder = HttpRequestBuilder()
    builder.url.takeFrom(url)
    builder.apply(block)

    return call(builder)
}

suspend fun HttpClient.call(url: String, block: HttpRequestBuilder.() -> Unit = {}): HttpResponse =
        call(URL(decodeURLPart(url)), block)

suspend inline fun <reified T> HttpResponse.receive(): T {
    if (T::class == HttpResponse::class) return this as T

    val container = call.receive(T::class)
    return container.response.payload as? T
            ?: error("Actual type: ${container.response.payload::class}, expected: ${T::class}")
}

