package io.ktor.client.response

import io.ktor.client.call.*
import io.ktor.client.utils.*
import io.ktor.http.*
import java.io.*
import java.util.*


class HttpResponse(
        val status: HttpStatusCode,
        val version: HttpProtocolVersion,
        val headers: Headers,
        val body: Any,
        val requestTime: Date,
        val responseTime: Date,
        val call: HttpClientCall,
        private val origin: Closeable?
) : Closeable {
    val cacheControl: HttpResponseCacheControl by lazy { headers.computeResponseCacheControl() }

    override fun close() {
        origin?.close()
    }
}

class HttpResponseBuilder() : Closeable {
    lateinit var status: HttpStatusCode
    lateinit var version: HttpProtocolVersion
    lateinit var body: Any
    lateinit var requestTime: Date
    lateinit var responseTime: Date

    val headers = HeadersBuilder(caseInsensitiveKey = true)
    val cacheControl: HttpResponseCacheControl get() = headers.computeResponseCacheControl()

    var origin: Closeable? = null

    constructor(response: HttpResponse) : this() {
        status= response.status
        version = response.version
        headers.appendAll(response.headers)
        body = response.body
        responseTime = response.responseTime
        requestTime = response.requestTime

        origin = response
    }

    fun headers(block: HeadersBuilder.() -> Unit) {
        headers.apply(block)
    }

    fun build(call: HttpClientCall): HttpResponse =
            HttpResponse(status, version, headers.build(), body, requestTime, responseTime, call, origin)

    override fun close() {
        origin?.close()
    }
}
