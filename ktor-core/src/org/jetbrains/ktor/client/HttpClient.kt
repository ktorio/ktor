package org.jetbrains.ktor.client

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.net.*
import java.util.concurrent.*

interface HttpConnection : Closeable {
    fun requestBlocking(init: RequestBuilder.() -> Unit) : HttpResponse
    suspend fun request(init: RequestBuilder.() -> Unit): HttpResponse

    @Deprecated("Use suspend fun request instead")
    fun requestAsync(init: RequestBuilder.() -> Unit, handler: (Future<HttpResponse>) -> Unit)
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

interface HttpClient {
    fun openConnection(host: String, port: Int, secure: Boolean = false): HttpConnection

    fun openBlocking(url: URL, block: RequestBuilder.() -> Unit = {}): HttpResponse = openConnection(url.host, url.computedPort(), url.protocol.toLowerCase() == "https").run {
        requestBlocking {
            path = url.path + if (url.query.isNullOrBlank()) "" else "?" + url.query
            block()
        }
    }

    fun openAsync(url: URL, block: RequestBuilder.() -> Unit, handler: (Future<HttpResponse>) -> Unit) {
        openConnection(url.host, url.computedPort(), url.protocol.toLowerCase() == "https").run {
            requestAsync({
                path = url.path + if (url.query.isNullOrBlank()) "" else "?" + url.query
                block()
            }, handler)
        }
    }

    private fun URL.computedPort() = when {
        port != -1 -> port
        protocol.toLowerCase() == "https" -> 443
        else -> 80
    }
}

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
