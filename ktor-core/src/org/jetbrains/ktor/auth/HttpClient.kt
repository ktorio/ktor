package org.jetbrains.ktor.auth.httpclient

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.net.*
import kotlin.properties.*

class RequestBuilder internal constructor() {
    private val headersBuilder = ValuesMapImpl.Builder()
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

interface HttpConnection : Closeable {
    fun request(init: RequestBuilder.() -> Unit)
    val responseHeaders: ValuesMap
    val responseStatus: HttpStatusCode
    val responseStream: InputStream
}

interface HttpClient {
    fun openConnection(host: String, port: Int, secure: Boolean = false): HttpConnection

    fun open(url: URL, block: RequestBuilder.() -> Unit) = openConnection(url.host, url.computedPort(), url.protocol.toLowerCase() == "https").apply {
        request {
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

object DefaultHttpClient : HttpClient {
    override fun openConnection(host: String, port: Int, secure: Boolean): HttpConnection = DefaultHttpConnection(host, port, secure)
}

private class DefaultHttpConnection(val host: String, val port: Int, val secure: Boolean): HttpConnection {
    private var connection by Delegates.notNull<HttpURLConnection>()

    override fun request(init: RequestBuilder.() -> Unit) {
        val builder = RequestBuilder()
        builder.init()

        val proto = if (secure) "https" else "http"
        val path = if (builder.path.startsWith("/")) builder.path else "/${builder.path}"
        connection = URL("$proto://$host:$port$path").openConnection() as HttpURLConnection
        connection.requestMethod = builder.method.value.toUpperCase()
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        builder.headers().forEach {
            connection.setRequestProperty(it.first, it.second)
        }

        builder.body?.let { body ->
            connection.doOutput = true
            connection.outputStream.buffered().use { os ->
                body(os)
            }
        }
    }

    override val responseHeaders: ValuesMap
        get() = ValuesMapImpl(connection.headerFields)

    override val responseStatus: HttpStatusCode
        get() = HttpStatusCode(connection.responseCode, connection.responseMessage)

    override val responseStream: InputStream
        get() = try {
            connection.inputStream
        } catch (t: Throwable) {
            connection.errorStream ?: "".byteInputStream()
        }

    override fun close() {
        try {
            connection.disconnect()
        } catch (t: Throwable) {
        }
    }
}
