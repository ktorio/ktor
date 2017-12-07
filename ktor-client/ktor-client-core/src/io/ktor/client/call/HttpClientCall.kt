package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.content.*
import java.io.*
import java.util.concurrent.atomic.*
import kotlin.reflect.*
import kotlin.reflect.full.*


class HttpClientCall private constructor(
        private val client: HttpClient
) : Closeable {
    private val received = AtomicBoolean(false)

    lateinit var request: HttpRequest
        private set

    lateinit var response: HttpResponse
        private set

    suspend fun receive(expectedType: KClass<*>): Any {
        if (response::class.isSubclassOf(expectedType)) return response
        if (!received.compareAndSet(false, true)) throw DoubleReceiveException(this)

        val subject = HttpResponseContainer(expectedType, response.receiveContent())
        val result = client.responsePipeline.execute(this, subject).response

        if (!result::class.isSubclassOf(expectedType)) throw NoTransformationFound(result::class, expectedType)
        return result
    }

    override fun close() {
        response.close()
    }

    companion object {
        suspend fun create(requestBuilder: HttpRequestBuilder, client: HttpClient): HttpClientCall {
            val call = HttpClientCall(client)
            call.request = client.createRequest(requestBuilder, call)

            val context = HttpRequestContext(client, call.request)
            val content = client.requestPipeline.execute(context, requestBuilder.body) as? OutgoingContent ?: error("")
            call.response = call.request.execute(content)

            return call
        }
    }
}

suspend fun HttpClient.call(block: HttpRequestBuilder.() -> Unit = {}): HttpClientCall =
        HttpClientCall.create(HttpRequestBuilder().apply(block), this)

suspend inline fun <reified T> HttpClientCall.receive(): T = receive(T::class) as T

class DoubleReceiveException(call: HttpClientCall) : IllegalStateException() {
    override val message: String = "Request already received: $call"
}

class NoTransformationFound(from: KClass<*>, to: KClass<*>) : UnsupportedOperationException() {
    override val message: String? = "No transformation found: $from -> $to"
}
