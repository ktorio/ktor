package io.ktor.client.engine.jetty

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.cio.internals.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.util.*

class JettyHttpResponse(
        override val call: HttpClientCall,
        override val status: HttpStatusCode,
        override val headers: Headers,
        override val requestTime: Date,
        private val content: ByteReadChannel,
        private val origin: Closeable
) : BaseHttpResponse {
    override val version = HttpProtocolVersion.HTTP_2_0
    override val responseTime = Date()

    override fun receiveContent(): IncomingContent = object : IncomingContent {
        override fun readChannel(): ByteReadChannel = content

        override fun multiPartData(): MultiPartData = TODO()

        override val headers: Headers = this@JettyHttpResponse.headers
    }

    override fun close() {
        origin.close()
    }
}