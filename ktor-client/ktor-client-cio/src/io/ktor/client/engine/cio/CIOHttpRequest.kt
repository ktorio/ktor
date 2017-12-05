package io.ktor.client.engine.cio

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.net.*
import java.util.*
import javax.net.ssl.*

class CIOHttpRequest(
        override val call: HttpClientCall,
        private val dispatcher: CoroutineDispatcher,
        builder: HttpRequestBuilder
) : HttpRequest {
    override val attributes: Attributes = Attributes()
    override val method: HttpMethod = builder.method
    override val url: Url = builder.url.build()

    override val headers: Headers = builder.headers.build()
    override val sslContext: SSLContext? = builder.sslContext

    override val context: Job = Job()

    init {
        require(url.scheme == "http") { "CIOEngine doesn't support https yet" }
    }

    suspend override fun execute(content: OutgoingContent): BaseHttpResponse {
        val requestTime = Date()
        val address = InetSocketAddress(url.host, url.port)
        val socket = aSocket().tcp().connect(address)
        val input = socket.openReadChannel()
        val output = socket.openWriteChannel()

        try {
            writeRequest(output, content)
            val response = parseResponse(input) ?: throw EOFException("Failed to parse HTTP response: unexpected EOF")
            val contentLength = response.headers["Content-Length"]?.toString()?.toLong() ?: -1L
            val transferEncoding = response.headers["Transfer-Encoding"]
            val connectionType = ConnectionType.parse(response.headers["Connection"])
            val responseBody = writer(dispatcher + context) {
                parseHttpBody(contentLength, transferEncoding, connectionType, input, channel)
            }

            val origin = Closeable {
                responseBody.cancel()
                response.release()
                output.close()
                socket.close()
            }

            return CIOHttpResponse(call, response, responseBody.channel, requestTime, origin)
        } catch (t: Throwable) {
            socket.close()
            throw t
        }
    }

    private suspend fun writeRequest(output: ByteWriteChannel, body: OutgoingContent) {
        val builder = RequestResponseBuilder()
        val bodySize = body.headers[HttpHeaders.ContentLength]?.toInt()

        try {
            builder.requestLine(method, url.fullPath, HttpProtocolVersion.HTTP_1_1.toString())
            builder.headerLine("Host", url.hostWithPort)

            if (!headers.contains(HttpHeaders.UserAgent)) {
                builder.headerLine("User-Agent", "CIO/ktor")
            }

            headers.flattenEntries().forEach { (name, value) ->
                builder.headerLine(name, value)
            }

            body.headers.flattenEntries().forEach { (name, value) ->
                builder.headerLine(name, value)
            }

            builder.emptyLine()
            output.writePacket(builder.build())
            output.flush()
        } finally {
            builder.release()
        }

        if (body is OutgoingContent.NoContent) return
        val chunked = bodySize == null || body.headers[HttpHeaders.TransferEncoding] == "chunked" || headers[HttpHeaders.TransferEncoding] == "chunked"

        launch(dispatcher + context) {
            val channel = if (chunked) encodeChunked(output, coroutineContext).channel else output
            try {
                writeBody(body, channel)
            } catch (cause: Throwable) {
                channel.close(cause)
            } finally {
                channel.close()
            }
        }
    }

    private suspend fun writeBody(body: OutgoingContent, channel: ByteWriteChannel) {
        when (body) {
            is OutgoingContent.NoContent -> return
            is OutgoingContent.ByteArrayContent -> channel.writeFully(body.bytes())
            is OutgoingContent.ReadChannelContent -> body.readFrom().copyTo(channel)
            is OutgoingContent.WriteChannelContent -> body.writeTo(channel)
            is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(body)
        }
    }
}

