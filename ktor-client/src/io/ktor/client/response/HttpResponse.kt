package io.ktor.client.response

import io.ktor.client.utils.*
import io.ktor.http.HttpStatusCode
import java.util.*


data class HttpResponse(
        val statusCode: HttpStatusCode,
        val reason: String,
        val version: HttpProtocolVersion,
        val headers: Headers,
        val payload: Any,
        val requestTime: Date,
        val responseTime: Date
) {
    val cacheControl: HttpResponseCacheControl by lazy { headers.computeResponseCacheControl() }
}

class HttpResponseBuilder() {
    constructor(response: HttpResponse) : this() {
        statusCode = response.statusCode
        reason = response.reason
        version = response.version
        headers.appendAll(response.headers)
        payload = response.payload
        responseTime = response.responseTime
        requestTime = response.requestTime
    }

    lateinit var statusCode: HttpStatusCode
    lateinit var reason: String
    lateinit var version: HttpProtocolVersion
    lateinit var payload: Any
    lateinit var requestTime: Date
    lateinit var responseTime: Date

    val headers = HeadersBuilder()

    val cacheControl: HttpResponseCacheControl get() = headers.computeResponseCacheControl()

    fun headers(block: HeadersBuilder.() -> Unit) {
        headers.apply(block)
    }

    fun build(): HttpResponse = HttpResponse(statusCode, reason, version, valuesOf(headers), payload, requestTime, responseTime)
}
