package io.ktor.client.call

import io.ktor.client.pipeline.HttpClientScope
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.response.HttpResponse
import io.ktor.client.response.HttpResponseBuilder
import io.ktor.client.response.HttpResponseContainer
import kotlin.reflect.KClass


class HttpClientCall(val request: HttpRequest, val response: HttpResponse, private val scope: HttpClientScope) {
    suspend fun receive(expectedType: KClass<*> = Unit::class): HttpResponseContainer {
        val subject = HttpResponseContainer(expectedType, request, HttpResponseBuilder(response))
        val container = scope.responsePipeline.execute(scope, subject)

        assert(container.response.payload::class == expectedType)
        return container
    }
}

suspend fun HttpClientScope.call(builder: HttpRequestBuilder): HttpClientCall =
        requestPipeline.execute(this, builder) as HttpClientCall

suspend fun HttpClientScope.call(block: HttpRequestBuilder.() -> Unit): HttpClientCall =
        call(HttpRequestBuilder().apply(block))

suspend inline fun <reified T> HttpClientCall.receive(): T = receive(T::class).response.payload as T

