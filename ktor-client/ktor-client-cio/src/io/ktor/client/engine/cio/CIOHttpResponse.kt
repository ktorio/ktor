package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.cio.internals.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.util.*

class CIOHttpResponse(
        override val call: HttpClientCall,
        response: Response,
        private val content: ByteReadChannel,
        override val requestTime: Date,
        private val origin: Closeable
) : BaseHttpResponse {

    override val status: HttpStatusCode = HttpStatusCode.fromValue(response.status)

    override val version: HttpProtocolVersion = HttpProtocolVersion.HTTP_1_1

    override val responseTime: Date = Date()

    override val headers: Headers = CIOHeaders(response.headers)

    override fun receiveContent(): IncomingContent = object : IncomingContent {
        override val headers: Headers = this@CIOHttpResponse.headers

        override fun readChannel(): ByteReadChannel = content

        override fun multiPartData(): MultiPartData = TODO()
    }

    override fun close() {
        origin.close()
    }
}
