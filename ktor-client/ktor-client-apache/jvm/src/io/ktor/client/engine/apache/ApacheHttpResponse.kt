package io.ktor.client.engine.apache

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.io.*
import kotlin.coroutines.*

internal class ApacheHttpResponse internal constructor(
    override val call: HttpClientCall,
    override val requestTime: GMTDate,
    private val engineResponse: org.apache.http.HttpResponse,
    override val content: ByteReadChannel,
    override val coroutineContext: CoroutineContext
) : HttpResponse {

    override val status: HttpStatusCode
    override val version: HttpProtocolVersion
    override val headers: Headers
    override val responseTime: GMTDate = GMTDate()

    init {
        val statusLine = engineResponse.statusLine

        status = HttpStatusCode(statusLine.statusCode, statusLine.reasonPhrase)
        version = with(engineResponse.protocolVersion) { HttpProtocolVersion.fromValue(protocol, major, minor) }
        headers = Headers.build {
            engineResponse.allHeaders.forEach { headerLine ->
                append(headerLine.name, headerLine.value)
            }
        }
    }
}
