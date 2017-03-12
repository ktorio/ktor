package org.jetbrains.ktor.testing

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.client.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*

class TestingHttpClient(val host: TestApplicationHost) : HttpClient(), AutoCloseable {
    override fun close() {
        host.dispose()
    }

    override suspend fun openConnection(host: String, port: Int, secure: Boolean): TestingHttpConnection {
        return TestingHttpConnection(this.host, host, port, secure)
    }
}

class TestingHttpConnection(val app: TestApplicationHost, val host: String, val port: Int, val secure: Boolean) : HttpConnection {

    fun requestBlocking(init: RequestBuilder.() -> Unit): HttpResponse {
        val builder = RequestBuilder()
        builder.init()

        val call = app.handleRequest(builder.method, builder.path) {
            builder.headers().forEach {
                addHeader(it.first, it.second)
            }

            builder.body?.let { content ->
                val bos = ByteArrayOutputStream()
                content(bos)
                body = bos.toByteArray().toString(Charsets.UTF_8)
            }
        }

        return TestingHttpResponse(this, call)
    }

    suspend override fun request(configure: RequestBuilder.() -> Unit): HttpResponse {
        return requestBlocking(configure)
    }

    override fun close() {
    }

    private class TestingHttpResponse(override val connection: HttpConnection, val call: TestApplicationCall) : HttpResponse {

        override val channel: ReadChannel
            get() = call.response.byteContent?.toReadChannel() ?: EmptyReadChannel

        override val headers: ValuesMap
            get() = call.response.headers.allValues()

        override val status: HttpStatusCode
            get() = call.response.status() ?: throw IllegalArgumentException("There is no status code assigned")

    }
}
