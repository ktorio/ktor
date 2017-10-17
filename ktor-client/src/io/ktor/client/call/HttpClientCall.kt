package io.ktor.client.call

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.takeFrom
import io.ktor.client.response.HttpResponse
import io.ktor.client.response.HttpResponseBuilder
import io.ktor.client.response.HttpResponseContainer
import io.ktor.client.utils.takeFrom
import io.ktor.http.decodeURLPart
import java.net.URL
import kotlin.reflect.KClass


data class HttpClientCall(val request: HttpRequest, val response: HttpResponse, private val scope: HttpClient) {
    suspend fun receive(expectedType: KClass<*> = Unit::class): HttpResponseContainer {
        val subject = HttpResponseContainer(expectedType, request, HttpResponseBuilder(response))
        val container = scope.responsePipeline.execute(scope, subject)

        assert(container.response.payload::class == expectedType)
        return container
    }
}

fun HttpClientCall.close() = response.close()

suspend fun HttpClient.call(builder: HttpRequestBuilder): HttpClientCall =
        requestPipeline.execute(this, HttpRequestBuilder().takeFrom(builder)) as HttpClientCall

suspend fun HttpClient.call(block: HttpRequestBuilder.() -> Unit = {}): HttpClientCall =
        call(HttpRequestBuilder().apply(block))

suspend fun HttpClient.call(url: URL, block: HttpRequestBuilder.() -> Unit = {}): HttpClientCall {
    val builder = HttpRequestBuilder()
    builder.url.takeFrom(url)
    builder.apply(block)

    return call(builder)
}

suspend fun HttpClient.call(url: String, block: HttpRequestBuilder.() -> Unit = {}): HttpClientCall =
        call(URL(decodeURLPart(url)), block)

suspend inline fun <reified T> HttpClientCall.receive(): T = receive(T::class).response.payload as T

