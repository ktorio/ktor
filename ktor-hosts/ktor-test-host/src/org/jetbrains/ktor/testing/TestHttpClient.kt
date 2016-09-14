package org.jetbrains.ktor.testing

import org.jetbrains.ktor.client.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.util.concurrent.*

class TestingHttpClient (val host: TestApplicationHost) : HttpClient, AutoCloseable {
    override fun close() {
        host.dispose()
    }

    override fun openConnection(host: String, port: Int, secure: Boolean) = TestingHttpConnection(this.host, host, port, secure)
}

class TestingHttpConnection(val app: TestApplicationHost, val host: String, val port: Int, val secure: Boolean) : HttpConnection {
    override fun requestBlocking(init: RequestBuilder.() -> Unit): HttpResponse {
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

    override fun requestAsync(init: RequestBuilder.() -> Unit, handler: (Future<HttpResponse>) -> Unit) {
        val f = try {
            CompletableFuture.completedFuture(requestBlocking(init))
        } catch (t: Throwable) {
            CompletableFuture<HttpResponse>().apply {
                completeExceptionally(t)
            }
        }

        handler(f)
    }

    override fun close() {
    }

    private class TestingHttpResponse(override val connection: HttpConnection, val call: TestApplicationCall) : HttpResponse {

        override val channel: ReadChannel
            get() = call.response.byteContent?.let { ByteArrayReadChannel(it) } ?: EmptyReadChannel

        override val headers: ValuesMap
            get() = call.response.headers.allValues()

        override val status: HttpStatusCode
            get() = call.response.status() ?: throw IllegalArgumentException("There is no status code assigned")

    }
}
