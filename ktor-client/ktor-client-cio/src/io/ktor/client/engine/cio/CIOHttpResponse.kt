package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.util.*

class CIOHttpResponse(
        override val call: HttpClientCall,
        override val requestTime: Date,
        private val input: ByteReadChannel,
        private val response: Response,
        private val origin: Closeable,
        dispatcher: CoroutineDispatcher
) : HttpResponse {
    override val status: HttpStatusCode = HttpStatusCode.fromValue(response.status)
    override val version: HttpProtocolVersion = HttpProtocolVersion.HTTP_1_1
    override val headers: Headers = ValuesMapBuilder().apply {
        val origin = CIOHeaders(response.headers)
        origin.names().forEach {
            appendAll(it, origin.getAll(it))
        }
    }.build()

    override val responseTime: Date = Date()

    override val executionContext: CompletableDeferred<Unit> = CompletableDeferred()

    private val content: ByteReadChannel

    init {
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L
        val transferEncoding = response.headers[HttpHeaders.TransferEncoding]
        val connectionType = ConnectionOptions.parse(response.headers[HttpHeaders.Connection])

        val writerJob = writer(dispatcher + executionContext) {
            parseHttpBody(contentLength, transferEncoding, connectionType, input, channel)
        }

        writerJob.invokeOnCompletion {
            executionContext.complete(Unit)
        }

        content = writerJob.channel
    }

    override fun receiveContent(): IncomingContent = object : IncomingContent {
        override val headers: Headers = this@CIOHttpResponse.headers

        override fun readChannel(): ByteReadChannel = content

        override fun multiPartData(): MultiPartData = throw UnsupportedOperationException()
    }

    override fun close() {
        response.release()
        content.cancel()
        origin.close()
        executionContext.complete(Unit)
    }
}

internal suspend fun ByteReadChannel.receiveResponse(
        call: HttpClientCall,
        requestTime: Date,
        dispatcher: CoroutineDispatcher,
        origin: Closeable
): CIOHttpResponse {
    val response = parseResponse(this) ?: throw EOFException("Failed to parse HTTP response: unexpected EOF")
    return CIOHttpResponse(call, requestTime, this, response, origin, dispatcher)
}