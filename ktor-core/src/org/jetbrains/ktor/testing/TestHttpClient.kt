package org.jetbrains.ktor.testing

import org.jetbrains.ktor.auth.httpclient.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import kotlin.properties.*

class TestingHttpClient (val app: TestApplicationHost) : HttpClient {
    override fun openConnection(host: String, port: Int, secure: Boolean) = TestingHttpConnection(app, host, port, secure)
}

class TestingHttpConnection(val app: TestApplicationHost, val host: String, val port: Int, val secure: Boolean) : HttpConnection {
    private var result by Delegates.notNull<RequestResult>()

    override fun request(init: RequestBuilder.() -> Unit) {
        val builder = RequestBuilder()
        builder.init()

        result = app.handleRequest(builder.method, builder.path) {
            builder.headers().forEach {
                addHeader(it.first, it.second)
            }

            builder.body?.let { content ->
                val bos = ByteArrayOutputStream()
                content(bos)
                body = bos.toByteArray().toString(Charsets.UTF_8)
            }
        }
    }

    override val responseStream: InputStream
        get() = result.response.content?.byteInputStream(Charsets.UTF_8) ?: "".byteInputStream()

    override val responseHeaders: ValuesMap
        get() = result.response.headers.allValues()

    override val responseStatus: HttpStatusCode
        get() = result.response.status() ?: throw IllegalArgumentException("There is no status code assigned")

    override fun close() {
    }
}
