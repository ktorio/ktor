package org.jetbrains.ktor.client

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.net.*
import java.nio.*
import javax.net.ssl.*

abstract class HttpClient {
    protected abstract suspend fun openConnection(host: String, port: Int, secure: Boolean = false): HttpConnection

    suspend fun request(url: URL, block: RequestBuilder.() -> Unit = {}): HttpResponse {
        val connection = openConnection(url.host, url.computedPort(), url.protocol.toLowerCase() == "https")
        return connection.request {
            path = url.path + if (url.query == null) "" else "?" + url.query
            block()
        }
    }

    private fun URL.computedPort() = when {
        port != -1 -> port
        protocol.toLowerCase() == "https" -> 443
        else -> 80
    }
}

interface HttpConnection : Closeable {
    suspend fun request(configure: RequestBuilder.() -> Unit): HttpResponse
}

interface HttpResponse : Closeable {
    val connection: HttpConnection

    val version: String
    val headers: ValuesMap
    val status: HttpStatusCode

    val channel: ReadChannel

    override fun close() {
        connection.close()
    }
}

val HttpResponse.stream: InputStream
    get() = channel.toInputStream()

fun HttpResponse.readText(): String = channel.toInputStream().reader(headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }?.charset() ?: Charsets.ISO_8859_1).use { it.readText() }

suspend fun HttpResponse.readBytes(size: Int): ByteArray {
    val result = ByteArray(size)
    val bb = ByteBuffer.wrap(result)
    val rch = channel

    while (bb.hasRemaining()) {
        if (rch.read(bb) == -1) throw IOException("Unexpected EOF, ${bb.remaining()} remaining of $size")
    }

    return result
}

suspend fun HttpResponse.readBytes(): ByteArray {
    val result = ByteArrayOutputStream()
    val bb = ByteBuffer.allocate(8192)
    val rch = channel

    while (true) {
        bb.clear()
        val rc = rch.read(bb)
        if (rc == -1) break
        bb.flip()

        result.write(bb.array(), bb.arrayOffset() + bb.position(), rc)
    }

    return result.toByteArray()
}

@Deprecated("", ReplaceWith("status.value"))
val HttpResponse.responseCode: Int get() = status.value

class RequestBuilder {
    private val headersBuilder = ValuesMapBuilder()
    var body: ((OutputStream) -> Unit)? = null
    var sslSocketFactory: SSLSocketFactory? = null
    var method = HttpMethod.Get
    var path = "/"
    var followRedirects = false

    fun header(name: String, value: String) {
        headersBuilder.append(name, value)
    }

    fun contentType(contentType: ContentType) {
        header(HttpHeaders.ContentType, contentType.toString())
    }

    fun headers() = headersBuilder.build().entries().flatMap { e -> e.value.map { e.key to it } }
}
