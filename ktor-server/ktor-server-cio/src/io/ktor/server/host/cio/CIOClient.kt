package io.ktor.server.host.cio

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import io.ktor.cio.*
import io.ktor.client.jvm.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import io.ktor.network.util.*
import io.ktor.util.*
import java.io.*
import java.net.*
import java.util.TreeSet

object CIOClient : HttpClient() {
    suspend override fun openConnection(host: String, port: Int, secure: Boolean): HttpConnection {
        require(secure == false) { "Coroutines HTTP client doesn't support https yet" }

        val address = InetSocketAddress(host, port) // resolution could be blocking
        val socket = aSocket().tcp().connect(address)

        return CIOHttpConnection(socket, if (port != 80) "$host:$port" else host)
    }
}

private class CIOHttpConnection(val socket: Socket, val defaultHost: String) : HttpConnection {
    private val input = socket.openReadChannel()
    private val output = socket.openWriteChannel()

    suspend override fun request(configure: RequestBuilder.() -> Unit): HttpResponse {
        val rb = RequestBuilder()
        configure(rb)

        val headers = rb.headers()
        val allHeaderNames = headers.mapTo(TreeSet(String.CASE_INSENSITIVE_ORDER), { it.first })
        val bodySize = if (rb.body != null && "Content-Length" in allHeaderNames)
            headers.first { it.first.equals("Content-Length", ignoreCase = true) }.second else null

        val builder = RequestResponseBuilder()
        try {
            builder.requestLine(HttpMethod(rb.method.value), rb.path, "HTTP/1.1")

            if ("Host" !in allHeaderNames) {
                builder.headerLine("Host", defaultHost)
            }
            if ("User-Agent" !in allHeaderNames) {
                builder.headerLine("User-Agent", "CIO/ktor")
            }
            if (rb.body != null && "Transfer-Encoding" !in allHeaderNames && bodySize == null) {
                builder.headerLine("Transfer-Encoding", "chunked")
            }

            headers.forEach { (name, value) ->
                builder.headerLine(name, value)
            }

            builder.emptyLine()
            output.writePacket(builder.build())
            output.flush()
        } finally {
            builder.release()
        }

        rb.body?.let { bodyWriter ->
            launch(ioCoroutineDispatcher) {
                if (bodySize != null) {
                    bodyWriter(OutputStreamAdapter(output, suppressClose = true))
                } else {
                    val encoder = encodeChunked(output, ioCoroutineDispatcher)
                    bodyWriter(OutputStreamAdapter(encoder.channel, suppressClose = false))
                    encoder.join()
                }
            }
        }

        val response = parseResponse(input) ?: throw EOFException("Failed to parse HTTP response: unexpected EOF")
        val responseBodyParser = writer(ioCoroutineDispatcher) {
            parseHttpBody(response.headers, input, channel)
        }

        return object : HttpResponse {
            override val connection: HttpConnection get() = this@CIOHttpConnection
            override val version: String = response.version.toString()
            override val headers: ValuesMap = CIOHeaders(response.headers)

            override val status: HttpStatusCode = HttpStatusCode.fromValue(response.status)
            override val channel: ReadChannel = CIOReadChannelAdapter(responseBodyParser.channel)

            override fun close() {
                responseBodyParser.cancel()
                response.release()
                super.close()
            }
        }

    }

    override fun close() {
        output.close()
        socket.close()
    }
}