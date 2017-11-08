package io.ktor.client.backend.cio

import io.ktor.client.*
import io.ktor.client.backend.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.network.sockets.*
import io.ktor.network.util.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.net.*
import java.util.*

class CIOBackend : HttpClientBackend {
    suspend override fun makeRequest(request: HttpRequest): HttpResponseBuilder {
        require(request.url.scheme == "http") { "Coroutines HTTP client doesn't support https yet" }

        val requestTime = Date()
        val address = InetSocketAddress(request.url.host, request.url.port)
        val socket = aSocket().tcp().connect(address)
        val input = socket.openReadChannel()
        val output = socket.openWriteChannel()

        val (response, responseBody) = try {
            writeRequest(request, output)
            val response = parseResponse(input) ?: throw EOFException("Failed to parse HTTP response: unexpected EOF")
            val responseBodyParser = writer(ioCoroutineDispatcher) { parseHttpBody(response.headers, input, channel) }

            response to responseBodyParser
        } catch (exception: IOException) {
            socket.close()
            throw exception
        }

        return HttpResponseBuilder().apply {
            status = HttpStatusCode(response.status, response.statusText.toString())
            version = HttpProtocolVersion.HTTP_1_1

            headers.appendAll(CIOHeaders(response.headers))
            body = ByteReadChannelBody(responseBody.channel)

            this.requestTime = requestTime
            responseTime = Date()

            origin = Closeable {
                responseBody.cancel()
                response.release()
                output.close()
                socket.close()
            }
        }
    }

    override fun close() {}

    companion object : HttpClientBackendFactory {
        override operator fun invoke(block: HttpClientBackendConfig.() -> Unit): HttpClientBackend = CIOBackend()
    }

    private suspend fun writeRequest(request: HttpRequest, output: ByteWriteChannel) {
        val builder = RequestResponseBuilder()
        val body = request.body as HttpMessageBody
        val bodySize = request.headers[HttpHeaders.ContentLength]?.toInt()

        try {
            builder.requestLine(
                    request.method,
                    request.url.fullPath,
                    HttpProtocolVersion.HTTP_1_1.toString()
            )
            builder.headerLine("Host", request.url.hostWithPort)

            if (!request.headers.contains(HttpHeaders.UserAgent)) {
                builder.headerLine("User-Agent", "CIO/ktor")
            }

            if (bodySize == null && body !is EmptyBody && !request.headers.contains(HttpHeaders.TransferEncoding)) {
                builder.headerLine("Transfer-Encoding", "chunked")
            }

            request.headers.flattenEntries().forEach { (name, value) ->
                builder.headerLine(name, value)
            }

            builder.emptyLine()
            output.writePacket(builder.build())
            output.flush()
        } finally {
            builder.release()
        }

        if (body is EmptyBody) return

        launch(ioCoroutineDispatcher) {
            if (bodySize == null) {
                val encoder = encodeChunked(output, ioCoroutineDispatcher)
                writeBody(body, encoder.channel)
                encoder.join()
            } else {
                writeBody(body, output)
            }
        }
    }

    private suspend fun writeBody(body: HttpMessageBody, channel: ByteWriteChannel) {
        when (body) {
            is ByteWriteChannelBody -> {
                body.block(channel)
                channel.close()
            }
            is ByteReadChannelBody -> body.channel.copyAndClose(channel)
        }
    }
}