package io.ktor.client.response

import io.ktor.client.utils.*
import io.ktor.http.HttpStatusCode
import java.io.Closeable
import java.util.*


data class HttpResponse(
        val statusCode: HttpStatusCode,
        val reason: String,
        val version: HttpProtocolVersion,
        val headers: Headers,
        val payload: Any,
        val requestTime: Date,
        val responseTime: Date,
        private val origin: Closeable?
) : Closeable {
    val cacheControl: HttpResponseCacheControl by lazy { headers.computeResponseCacheControl() }

    override fun close() {
        origin?.close()
    }
}

class HttpResponseBuilder() : Closeable {
    lateinit var statusCode: HttpStatusCode
    lateinit var reason: String
    lateinit var version: HttpProtocolVersion
    lateinit var payload: Any
    lateinit var requestTime: Date
    lateinit var responseTime: Date

    val headers = HeadersBuilder()
    val cacheControl: HttpResponseCacheControl get() = headers.computeResponseCacheControl()

    var origin: Closeable? = null

    constructor(response: HttpResponse) : this() {
        statusCode = response.statusCode
        reason = response.reason
        version = response.version
        headers.appendAll(response.headers)
        payload = response.payload
        responseTime = response.responseTime
        requestTime = response.requestTime

        origin = response
    }

    fun headers(block: HeadersBuilder.() -> Unit) {
        headers.apply(block)
    }

    fun build(): HttpResponse = HttpResponse(statusCode, reason, version, valuesOf(headers), payload, requestTime, responseTime, origin)

    override fun close() {
        origin?.close()
    }
}
