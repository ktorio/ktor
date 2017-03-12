package org.jetbrains.ktor.client

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.net.*

abstract class HttpClient {
    protected abstract suspend fun openConnection(host: String, port: Int, secure: Boolean = false): HttpConnection

    suspend fun request(url: URL, block: RequestBuilder.() -> Unit = {}): HttpResponse {
        val connection = openConnection(url.host, url.computedPort(), url.protocol.toLowerCase() == "https")
        return connection.request {
            path = url.path + if (url.query.isNullOrBlank()) "" else "?" + url.query
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

    val headers: ValuesMap
    val status: HttpStatusCode

    val channel: ReadChannel

    override fun close() {
        connection.close()
    }
}

val HttpResponse.stream: InputStream
    get() = channel.toInputStream()


class RequestBuilder {
    private val headersBuilder = ValuesMapBuilder()
    var body: ((OutputStream) -> Unit)? = null
    var method = HttpMethod.Get
    var path = "/"

    fun header(name: String, value: String) {
        headersBuilder.append(name, value)
    }

    fun contentType(contentType: ContentType) {
        header(HttpHeaders.ContentType, contentType.toString())
    }

    fun headers() = headersBuilder.build().entries().flatMap { e -> e.value.map { e.key to it } }
}
