/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.posix.AF_INET
import platform.posix.EINTR
import platform.posix.MSG_NOSIGNAL
import platform.posix.SHUT_WR
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.errno
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.shutdown
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.timeval

/** A Ktor application on one kron reactor, on a port the kernel picks. */
class TestServer(module: suspend Application.() -> Unit) : AutoCloseable {
    val server: EmbeddedServer<KronApplicationEngine, KronApplicationEngine.Configuration> =
        embeddedServer(Kron, configure = {
            connector { port = 0 }
            reactorCount = 1
        }, module = module).start(wait = false)

    val port: Int = runBlocking { server.engine.resolvedConnectors().single().port }

    fun connect(): TestClient = TestClient(port)

    override fun close() = server.stop(0, 0)
}

class Response(val status: Int, val reason: String, val headers: List<Pair<String, String>>, val body: ByteArray) {
    fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

    val text: String get() = body.decodeToString()
}

/** A blocking HTTP/1.1 client on a raw socket: what it writes is exactly what the test gives it. */
class TestClient(port: Int) : AutoCloseable {
    private val fd: Int = socket(AF_INET, SOCK_STREAM, 0).also { check(it >= 0) { "socket failed with errno $errno" } }
    private val buffer = ByteArray(64 * 1024)
    private var start = 0
    private var end = 0

    init {
        memScoped {
            val tv = alloc<timeval>()
            tv.tv_sec = 5
            tv.tv_usec = 0
            setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, tv.ptr, sizeOf<timeval>().convert())

            val addr = alloc<sockaddr_in>()
            addr.sin_family = AF_INET.convert()
            addr.sin_port = (((port and 0xFF) shl 8) or ((port ushr 8) and 0xFF)).toUShort()
            addr.sin_addr.s_addr = 0x0100007Fu // 127.0.0.1, network order
            check(platform.posix.connect(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0) {
                "connect failed with errno $errno"
            }
        }
    }

    fun write(bytes: ByteArray) {
        bytes.usePinned { pinned ->
            var offset = 0
            while (offset < bytes.size) {
                val n = send(fd, pinned.addressOf(offset), (bytes.size - offset).convert(), MSG_NOSIGNAL)
                if (n < 0) {
                    if (errno == EINTR) continue
                    throw IllegalStateException("send failed with errno $errno")
                }
                offset += n.toInt()
            }
        }
    }

    fun write(text: String) = write(text.encodeToByteArray())

    fun shutdownOutput() {
        shutdown(fd, SHUT_WR)
    }

    /** One response, its body framed by Content-Length, chunked, or the end of the stream. */
    fun readResponse(head: Boolean = false): Response {
        val statusLine = readLine() ?: throw IllegalStateException("the connection ended before a response")
        val parts = statusLine.split(' ', limit = 3)
        val headers = ArrayList<Pair<String, String>>()
        while (true) {
            val line = readLine() ?: throw IllegalStateException("the connection ended inside a head")
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            headers.add(line.substring(0, colon) to line.substring(colon + 1).trim())
        }

        val response = Response(parts[1].toInt(), parts.getOrElse(2) { "" }, headers, ByteArray(0))
        val status = response.status
        if (head || status / 100 == 1 || status == 204 || status == 304) {
            return response
        }

        val length = response.header("Content-Length")
        val body = when {
            length != null -> readExactly(length.toInt())
            response.header("Transfer-Encoding") == "chunked" -> readChunked()
            else -> readToEnd()
        }

        return Response(response.status, response.reason, headers, body)
    }

    /** True once the server has closed the connection: nothing more comes. */
    fun isClosedByPeer(): Boolean = start == end && fill() <= 0

    fun readToEnd(): ByteArray {
        val out = ArrayList<Byte>()
        while (true) {
            if (start == end && fill() <= 0) return out.toByteArray()
            while (start < end) out.add(buffer[start++])
        }
    }

    private fun readChunked(): ByteArray {
        val out = ArrayList<Byte>()
        while (true) {
            val size = readLine()!!.substringBefore(';').trim().toInt(16)
            if (size == 0) {
                while (readLine()!!.isNotEmpty()) {
                    // trailers
                }
                return out.toByteArray()
            }
            for (b in readExactly(size)) out.add(b)
            check(readLine() == "") { "a chunk did not end with CRLF" }
        }
    }

    fun readBytes(count: Int): ByteArray = readExactly(count)

    private fun readExactly(count: Int): ByteArray {
        val out = ByteArray(count)
        var got = 0
        while (got < count) {
            if (start == end &&
                fill() <= 0
            ) {
                throw IllegalStateException("the connection ended after $got of $count bytes")
            }
            val n = minOf(end - start, count - got)
            buffer.copyInto(out, got, start, start + n)
            start += n
            got += n
        }
        return out
    }

    private fun readLine(): String? {
        val line = StringBuilder()
        while (true) {
            if (start == end && fill() <= 0) return if (line.isEmpty()) null else line.toString()
            val b = buffer[start++].toInt().toChar()
            if (b == '\n') return line.toString().removeSuffix("\r")
            line.append(b)
        }
    }

    // Bytes read into the buffer, 0 at the end of the stream; a read that times out throws.
    private fun fill(): Int {
        start = 0
        end = 0
        while (true) {
            val n = buffer.usePinned { recv(fd, it.addressOf(0), buffer.size.convert(), 0) }
            if (n >= 0) {
                end = n.toInt()
                return end
            }
            if (errno == EINTR) continue
            throw IllegalStateException("recv failed with errno $errno")
        }
    }

    override fun close() {
        platform.posix.close(fd)
    }
}
