package io.ktor.network.tls

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.net.*

fun main(args: Array<String>) {
    val urlString = args.firstOrNull() ?: "https://kotlinlang.org"
    val url = URL(urlString)

    if (url.protocol != "https") throw IllegalArgumentException("Only https is supported")

    val host = url.host
    val port = url.port.takeIf { it > 0 }?.toInt() ?: 443
    val pathAndQuery = (url.path + (url.query?.let { "?" + it } ?: "")).trim().let { if (it.startsWith("/")) it else "/$it" }

    val remoteAddress = InetSocketAddress(host, port)

    runBlocking {
        ActorSelectorManager(ioCoroutineDispatcher).use { selector ->
            aSocket(selector).tcp().connect(remoteAddress).tls(serverName = host).use { socket ->
                val input = socket.openReadChannel()
                val output = socket.openWriteChannel()

                val request = RequestResponseBuilder().apply {
                    requestLine(HttpMethod.Get, pathAndQuery, "HTTP/1.1")
                    headerLine("Host", "$host:$port")
                    headerLine("Accept", "*/*")
                    headerLine("User-Agent", "kotlinx-http-over-tls")
                    headerLine("Connection", "close")
                    emptyLine()
                }.build()
                output.writePacket(request)
                output.flush()

                val bb = ByteBuffer.allocate(8192)
                while (true) {
                    val rc = input.readAvailable(bb)
                    if (rc == -1) break
                    bb.flip()
                    System.out.write(bb.array(), bb.arrayOffset() + bb.position(), rc)
                    System.out.flush()
                }

                output.close()
            }
        }
    }
}
