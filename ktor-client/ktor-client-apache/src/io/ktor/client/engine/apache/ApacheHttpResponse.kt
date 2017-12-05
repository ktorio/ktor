package io.ktor.client.engine.apache

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.content.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.io.*
import java.util.*

class ApacheHttpResponse internal constructor(
        override val call: HttpClientCall,
        private val response: ApacheEngineResponse,
        private val content: ByteReadChannel,
        override val requestTime: Date
) : BaseHttpResponse {
    override val status: HttpStatusCode
    override val version: HttpProtocolVersion
    override val headers: Headers
    override val responseTime: Date = Date()

    init {
        val response = response.engineResponse
        val (code, reason) = with(response.statusLine) { statusCode to reasonPhrase }

        status = if (reason != null) HttpStatusCode(code, reason) else HttpStatusCode.fromValue(code)
        version = with(response.protocolVersion) { HttpProtocolVersion(protocol, major, minor) }
        headers = HeadersBuilder().apply {
            response.allHeaders.forEach { headerLine ->
                append(headerLine.name, headerLine.value)
            }
        }.build()
    }

    override fun receiveContent(): IncomingContent = object : IncomingContent {
        override val headers: Headers = this@ApacheHttpResponse.headers

        override fun readChannel(): ByteReadChannel = content

        override fun multiPartData(): MultiPartData = TODO()
    }

    override fun close() {
        response.responseReader.close()
    }
}