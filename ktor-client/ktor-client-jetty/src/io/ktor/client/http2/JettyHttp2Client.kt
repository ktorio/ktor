package io.ktor.client.http2

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.future.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.http2.*
import org.eclipse.jetty.http2.api.*
import org.eclipse.jetty.http2.client.*
import org.eclipse.jetty.http2.frames.*
import org.eclipse.jetty.util.*
import org.eclipse.jetty.util.ssl.*
import io.ktor.cio.*
import io.ktor.client.jvm.*
import io.ktor.http.*
import io.ktor.util.*
import java.io.*
import java.net.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*
import kotlin.coroutines.experimental.*

object Http2Client : HttpClient() {
    suspend override fun openConnection(host: String, port: Int, secure: Boolean): HttpConnection =
            Http2Connection(host, port, secure).connect()
}

private class Http2Connection(val host: String, val port: Int, val secure: Boolean) : HttpConnection {
    private lateinit var session: Session

    val sslContextFactory = SslContextFactory(true)
    private val jettyClient = HTTP2Client().apply {
        addBean(sslContextFactory)
    }

    private val hostPort = HostPortHttpField("$host:$port")

    suspend fun connect(): Http2Connection {
        jettyClient.start()

        session = connect(host, port).apply {
            this.settings(SettingsFrame(kotlin.collections.emptyMap(), true), org.eclipse.jetty.util.Callback.NOOP)
        }

        return this
    }

    suspend override fun request(configure: RequestBuilder.() -> Unit): HttpResponse {
        val builder = RequestBuilder()
        configure(builder)

        val rr = stream(builder)
        sendBody(rr.stream, builder)

        rr.awaitStatus()

        return rr
    }

    override fun close() {
        try {
            runBlocking {
                withCallback<Unit> {
                    session.close(0, null, it)
                }
            }
        } finally {
            jettyClient.stop()
        }
    }

    private suspend fun sendBody(stream: Stream, rb: RequestBuilder) {
        rb.body?.let { body ->
            val failures = ArrayBlockingQueue<Throwable>(1)
            var outstanding = 0
            val l = ReentrantLock()

            val sent = l.newCondition()!!
            val empty = l.newCondition()!!

            val os = object : OutputStream(), Callback {
                override fun succeeded() {
                    resume(false)
                }

                override fun failed(x: Throwable) {
                    failures.offer(x)
                    resume(true)
                }

                private fun resume(all: Boolean) {
                    l.withLock {
                        outstanding--
                        sent.signalAll()

                        if (all || outstanding == 0) {
                            empty.signalAll()
                        }
                    }
                }

                override fun write(b: Int) {
                    writeEnter()

                    val f = DataFrame(stream.id, ByteBuffer.wrap(byteArrayOf(b.toByte()), 0, 1), false)
                    stream.data(f, this)
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    writeEnter()

                    val f = DataFrame(stream.id, ByteBuffer.wrap(b, off, len), false)
                    stream.data(f, this)
                }

                private fun writeEnter() {
                    l.withLock {
                        checkFailures()

                        while (outstanding == 5) {
                            sent.await()
                            checkFailures()
                        }

                        outstanding++
                    }
                }

                override fun flush() {
                    l.withLock {
                        while (true) {
                            checkFailures()
                            if (outstanding == 0) break
                            empty.await()
                        }
                    }
                }

                private fun checkFailures() {
                    failures.peek()?.let { throw it }
                }
            }

            body(os)
            os.flush()

            stream.data(DataFrame(stream.id, ByteBuffer.allocate(0), true), Callback.NOOP)
        }
    }

    private suspend fun stream(builder: RequestBuilder): RequestResponse {
        val headers = HttpFields()

        for ((name, value) in builder.headers()) {
            headers.add(name, value)
        }

        val meta = MetaData.Request(builder.method.value, if (secure) "https" else "http", hostPort, builder.path, HttpVersion.HTTP_2, headers, Long.MIN_VALUE)

        val headersFrame = HeadersFrame(meta, null, builder.body == null)

        val rr = RequestResponse(this)
        val stream = withPromise<Stream> { session.newStream(headersFrame, it, rr.Listener) }

        rr.stream = stream

        return rr
    }

    private suspend fun connect(host: String, port: Int): Session {
        return withPromise { promise ->
            jettyClient.connect(sslContextFactory, InetSocketAddress(host, port), Session.Listener.Adapter(), promise)
        }
    }
}

private class RequestResponse(override val connection: Http2Connection) : ReadChannel, HttpResponse {
    lateinit var stream: Stream

    private val headersBuilder = ValuesMapBuilder(caseInsensitiveKey = true)
    private val data = Channel<Pair<ByteBuffer, Callback>>(Channel.UNLIMITED)
    private var current: Pair<ByteBuffer, Callback>? = null
    private val cf = CompletableFuture<HttpStatusCode?>()

    override val version: String
        get() = "HTTP/2"

    override val headers: ValuesMap by lazy { headersBuilder.build() }

    override val status: HttpStatusCode
        get() = cf.getNow(null) ?: throw IllegalStateException("No response yet")

    override val channel: ReadChannel
        get() = this

    suspend fun awaitStatus(): HttpStatusCode {
        cf.await()
        return cf.get() ?: throw IOException("Connection reset")
    }

    val Listener = object : Stream.Listener {
        override fun onPush(stream: Stream, frame: PushPromiseFrame): Stream.Listener {
            stream.reset(ResetFrame(frame.promisedStreamId, ErrorCode.CANCEL_STREAM_ERROR.code), Callback.NOOP)
            return Ignore
        }

        override fun onReset(stream: Stream, frame: ResetFrame) {
            if (frame.error == 0) data.close()
            else if (frame.error == ErrorCode.CANCEL_STREAM_ERROR.code) data.close(ClosedChannelException())
            else {
                val code = ErrorCode.from(frame.error)

                data.close(IOException("Connection reset ${code?.name ?: "with unknown error code ${frame.error}"}") )
            }

            cf.complete(null)
        }

        override fun onData(stream: Stream, frame: DataFrame, callback: Callback) {
            try {
                if (frame.data.remaining() > 0) {
                    if (!data.offer(Pair(frame.data.copy(), callback))) {
                        throw IllegalStateException("data.offer() failed")
                    }
                }

                if (frame.isEndStream) {
                    data.close()
                }
            } catch (t: Throwable) {
                callback.failed(t)
            }
        }

        override fun onHeaders(stream: Stream, frame: HeadersFrame) {
            frame.metaData.fields.forEach { field ->
                headersBuilder.append(field.name, field.value)
            }

            if (frame.isEndStream) {
                data.close()
            }

            cf.complete((frame.metaData as? MetaData.Response)?.let {
                HttpStatusCode(it.status, it.reason ?: "")
            })
        }
    }

    suspend override fun read(dst: ByteBuffer): Int {
        val frame = current ?: data.poll()

        return if (frame != null) {
            readImpl(frame, dst)
        } else if (data.isClosedForReceive) {
            -1
        } else {
            readSuspend(dst)
        }
    }

    private suspend fun readSuspend(dst: ByteBuffer): Int {
        val framePair = data.receiveOrNull() ?: return -1
        return readImpl(framePair, dst)
    }

    private fun readImpl(framePair: Pair<ByteBuffer, Callback>, dst: ByteBuffer): Int {
        val buffer = framePair.first

        val rc = buffer.putTo(dst)

        if (buffer.hasRemaining()) {
            current = framePair
        } else {
            current = null
            framePair.second.succeeded()
        }

        return rc
    }

    override fun close() {
        data.close()
        connection.close()
    }

    companion object {
        private val Ignore = Stream.Listener.Adapter()
    }
}

private suspend fun <R> withPromise(block: (Promise<R>) -> Unit): R {
    return suspendCoroutine { continuation ->
        block(PromiseContinuation(continuation))
    }
}

private suspend fun <R> withCallback(block: (Callback) -> Unit) {
    return suspendCoroutine { continuation ->
        block(CallbackContinuation(continuation))
    }
}

private class PromiseContinuation<R>(val continuation: Continuation<R>) : Promise<R> {
    override fun failed(x: Throwable) {
        continuation.resumeWithException(x)
    }

    override fun succeeded(result: R) {
        continuation.resume(result)
    }
}

private class CallbackContinuation(val continuation: Continuation<Unit>) : Callback {
    override fun succeeded() {
        continuation.resume(Unit)
    }

    override fun failed(x: Throwable) {
        continuation.resumeWithException(x)
    }
}