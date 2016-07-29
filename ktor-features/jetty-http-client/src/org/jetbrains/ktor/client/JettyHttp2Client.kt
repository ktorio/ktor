package org.jetbrains.ktor.client

import org.eclipse.jetty.client.api.*
import org.eclipse.jetty.http2.client.*
import org.eclipse.jetty.http2.client.http.*
import org.eclipse.jetty.util.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

object JettyHttp2Client : HttpClient {
    override fun openConnection(host: String, port: Int, secure: Boolean): HttpConnection {
        return JettyHttp2Connection(host, port, secure)
    }

    private class JettyHttp2Response(override val connection: JettyHttp2Connection, val response: Response, override val channel: ReadChannel) : HttpResponse {
        override val status: HttpStatusCode
            get() = HttpStatusCode(response.status, response.reason ?: "")

        override val headers: ValuesMap by lazy { ValuesMapBuilder(true).apply {
            response.headers.forEach { append(it.name, it.value) }
        }.build() }
    }

    private class JettyClientReadChannel(val request: Request) : ReadChannel {
        private val currentHandler = AtomicReference<AsyncHandler?>()
        private var currentBuffer: ByteBuffer? = null

        private var contentEof = false
        private val contentBuffers = Collections.synchronizedList(ArrayList<ByteBuffer>())
        private var contentCallbacks = Collections.synchronizedList(ArrayList<Callback>())
        private var contentError: Throwable? = null


        init {
            request.onResponseContentAsync { response, buffer, callback ->
                if (buffer.hasRemaining()) {
                    contentCallbacks.add(callback)
                    contentBuffers.add(buffer)

                    tryMeet()
                } else {
                    callback.succeeded()
                }
            }
            request.onResponseSuccess {
                contentEof = true

                tryMeet()
            }
            request.onResponseFailure { response, throwable ->
                contentEof = true
                contentError = throwable

                tryMeet()
            }
        }

        override fun read(dst: ByteBuffer, handler: AsyncHandler) {
            if (contentError != null) {
                val error = contentError!!
                return handler.failed(error)
            }
            if (contentEof && contentBuffers.isEmpty()) {
                handler.successEnd()
                return
            }
            if (!currentHandler.compareAndSet(null, handler)) {
                throw IllegalStateException("Read operation is already in progress")
            }
            currentBuffer = dst

            tryMeet()
        }

        override fun close() {
            if (!contentEof) {
                request.abort(IOException("Channel closed"))
            }
        }

        private val meetCounter = AtomicInteger()
        private fun tryMeet() {
            if (currentHandler.get() != null) {
                when {
                    contentError != null -> contentError?.let { meetError(it) }
                    contentBuffers.isNotEmpty() -> meetBuffers()
                    contentEof -> meetEof()
                }
            }
        }

        private fun meetBuffers() {
            withCounter {
                val dst = currentBuffer
                val handler = currentHandler.get()!!

                if (dst != null) {
                    var copied = 0
                    while (contentBuffers.isNotEmpty() && dst.hasRemaining()) {
                        val buffer = contentBuffers.first()
                        copied += buffer.putTo(dst)
                        if (!buffer.hasRemaining()) {
                            contentBuffers.removeAt(0)
                            contentCallbacks.removeAt(0).succeeded()
                        }
                    }

                    if (copied > 0 || !dst.hasRemaining()) {
                        currentBuffer = null
                        currentHandler.set(null)

                        handler.success(copied)
                    }
                } // else we get one more tryMeet iteration that will succeed
            }
        }

        private fun meetEof() {
            withCounter {
                currentBuffer = null
                currentHandler.getAndSet(null)?.successEnd()
            }
        }

        private fun meetError(error: Throwable) {
            withCounter {
                currentBuffer = null
                contentError = null
                contentCallbacks.forEach {
                    it.failed(error)
                }
                contentCallbacks.clear()
                contentBuffers.clear()

                currentHandler.getAndSet(null)?.failed(error)
            }

            tryMeet()
        }

        private inline fun withCounter(block: () -> Unit) {
            if (meetCounter.compareAndSet(0, 1)) {
                try {
                    block()
                } finally {
                    meetCounter.compareAndSet(1, 0)
                    tryMeet()
                }
            }
        }
    }

    private class JettyRequestProcessor(val request: Request, val connection: JettyHttp2Connection, handler: (Future<HttpResponse>) -> Unit) {
        private val channel = JettyClientReadChannel(request)

        init {
            request.onResponseBegin { response ->
                handler(CompletableFuture.completedFuture(JettyHttp2Response(connection, response, channel)))
            }
            request.onRequestFailure { request, throwable ->
                handler(CompletableFuture<HttpResponse>().apply {
                    completeExceptionally(throwable)
                })
            }
        }

        fun send() {
            request.send { result ->
            }
        }
    }

    private class JettyHttp2Connection(val host: String, val port: Int, secure: Boolean) : HttpConnection {
        private val transport = HTTP2Client()
        private val client = org.eclipse.jetty.client.HttpClient(HttpClientTransportOverHTTP2(transport), null).apply {
            isConnectBlocking = false
            isStrictEventOrdering = true
        } // TODO: SSL context

        override fun requestBlocking(init: RequestBuilder.() -> Unit): HttpResponse {
            val future = CompletableFuture<HttpResponse>()

            requestAsync(init, {
                try {
                    future.complete(it.get())
                } catch (t: Throwable) {
                    future.completeExceptionally(t)
                }
            })

            return future.get()
        }

        override fun requestAsync(init: RequestBuilder.() -> Unit, handler: (Future<HttpResponse>) -> Unit) {
            ensureRunning()
            JettyRequestProcessor(newRequest(RequestBuilder().apply(init)), this, handler).send()
        }

        private fun newRequest(builder: RequestBuilder): Request {
            val request = client.newRequest(host, port)!!

            request.method(builder.method.value)
            for ((name, value) in builder.headers()) {
                request.header(name, value)
            }
            request.path(builder.path)

            return request
        }

        override fun close() {
            client.stop()
            transport.stop()
        }

        private fun ensureRunning() {
            if (!transport.isStarted) {
                transport.start()
            }
            if (!client.isStarted) {
                client.start()
            }
        }
    }
}