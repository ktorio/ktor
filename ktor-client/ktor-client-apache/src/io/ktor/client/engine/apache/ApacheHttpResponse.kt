package io.ktor.client.engine.apache

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*

internal class ApacheHttpResponse internal constructor(
    override val call: HttpClientCall,
    override val requestTime: GMTDate,
    override val executionContext: CompletableDeferred<Unit>,
    private val engineResponse: org.apache.http.HttpResponse,
    override val content: ByteReadChannel
) : HttpResponse {
    override val status: HttpStatusCode
    override val version: HttpProtocolVersion
    override val headers: Headers
    override val responseTime: GMTDate = GMTDate()

    init {
        val code = engineResponse.statusLine.statusCode

        status = HttpStatusCode.fromValue(code)
        version = with(engineResponse.protocolVersion) { HttpProtocolVersion.fromValue(protocol, major, minor) }
        headers = Headers.build {
            engineResponse.allHeaders.forEach { headerLine ->
                append(headerLine.name, headerLine.value)
            }
        }
    }

    override fun close() {
        executionContext.complete(Unit)
    }
}
