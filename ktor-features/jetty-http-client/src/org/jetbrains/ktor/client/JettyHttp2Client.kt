package org.jetbrains.ktor.client

import org.eclipse.jetty.client.api.*
import org.eclipse.jetty.http2.client.*
import org.eclipse.jetty.http2.client.http.*
import org.eclipse.jetty.util.*
import org.eclipse.jetty.util.ssl.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.*
import java.util.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

object JettyHttp2Client : HttpClient() {
    override suspend fun openConnection(host: String, port: Int, secure: Boolean): HttpConnection {
        return JettyHttp2Connection(host, port, secure)
    }

    private class JettyHttp2Response(override val connection: JettyHttp2Connection, val response: Response, override val channel: ReadChannel) : HttpResponse {
        override val status: HttpStatusCode
            get() = HttpStatusCode(response.status, response.reason ?: "")

        override val headers: ValuesMap by lazy {
            ValuesMap.build(true) {
                response.headers.forEach { append(it.name, it.value) }
            }
        }
    }

    private class JettyClientReadChannel(val request: Request) : ReadChannel {
        private val currentHandler = AtomicReference<Continuation<Int>?>()
        private var currentBuffer: ByteBuffer? = null

        private var contentEof = false
        private val contentBuffers = Collections.synchronizedList(ArrayList<ByteBuffer>())
        private var contentCallbacks = Collections.synchronizedList(ArrayList<Callback>())
        private var contentError: Throwable? = null


        init {
            request.onResponseContentAsync { _, buffer, callback ->
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
            request.onResponseFailure { _, throwable ->
                contentEof = true
                contentError = throwable

                tryMeet()
            }
        }

        override suspend fun read(dst: ByteBuffer): Int {
            contentError?.let { throw it }
            if (contentEof && contentBuffers.isEmpty()) {
                return -1
            }

            return suspendCoroutine { continuation ->
                if (!currentHandler.compareAndSet(null, continuation)) {
                    throw IllegalStateException("Read operation is already in progress")
                }
                currentBuffer = dst

                tryMeet()
            }
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

                        handler.resume(copied)
                    }
                } // else we get one more tryMeet iteration that will succeed
            }
        }

        private fun meetEof() {
            withCounter {
                currentBuffer = null
                currentHandler.getAndSet(null)?.resume(-1)
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

                currentHandler.getAndSet(null)?.resumeWithException(error)
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

    private class JettyRequestProcessor(val request: Request, val connection: JettyHttp2Connection, handler: Continuation<HttpResponse>) {
        private val channel = JettyClientReadChannel(request)

        init {
            request.onResponseBegin { response ->
                handler.resume(JettyHttp2Response(connection, response, channel))
            }
            request.onRequestFailure { _, throwable ->
                handler.resumeWithException(throwable)
            }
        }

        fun send() {
            request.send {
            }
        }
    }

    private class JettyHttp2Connection(val host: String, val port: Int, secure: Boolean) : HttpConnection {
        val ssl = if (secure) SslContextFactory(true) else null

        private val transport = HTTP2Client()
        private val client = org.eclipse.jetty.client.HttpClient(HttpClientTransportOverHTTP2(transport), ssl).apply {
            isConnectBlocking = false
            isStrictEventOrdering = true

            if (ssl != null) {
                addBean(ssl)
            }
        }

        suspend override fun request(configure: RequestBuilder.() -> Unit): HttpResponse {
            ensureRunning()
            return suspendCoroutine { continuation ->
                JettyRequestProcessor(newRequest(RequestBuilder().apply(configure)), this, continuation).send()
            }
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