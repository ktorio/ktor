package org.jetbrains.ktor.client.http2

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.future.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.http2.*
import org.eclipse.jetty.http2.api.*
import org.eclipse.jetty.http2.client.*
import org.eclipse.jetty.http2.frames.*
import org.eclipse.jetty.util.*
import org.eclipse.jetty.util.ssl.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.client.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.net.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*
import kotlin.coroutines.experimental.*

object Http2Client : HttpClient() {
    suspend override fun openConnection(host: String, port: Int, secure: Boolean): HttpConnection {
        return Http2Connection(host, port, secure).connect()
    }
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
        val rr = stream(RequestBuilder().apply(configure))
        rr.awaitStatus()

        return rr
    }

    override fun close() {
        runBlocking {
            withCallback<Unit> {
                session.close(0, null, it)
            }

            jettyClient.stop()
        }
    }

    private suspend fun stream(builder: RequestBuilder): RequestResponse {
        val headers = HttpFields()

        for ((name, value) in builder.headers()) {
            headers.add(name, value)
        }

        val meta = MetaData.Request(builder.method.value, if (secure) "https" else "http", hostPort, builder.path, HttpVersion.HTTP_2, headers, Long.MIN_VALUE)

        val headersFrame = HeadersFrame(meta, null, true)

        val rr = RequestResponse(this)
        val stream = withPromise<Stream> { session.newStream(headersFrame, it, rr.Listener) }

        rr.stream = stream

        return rr
    }

    private suspend fun connect(host: String, port: Int): Session {
        return withPromise { promoise ->
            jettyClient.connect(sslContextFactory, InetSocketAddress(host, port), Session.Listener.Adapter(), promoise)
        }
    }
}

private class RequestResponse(override val connection: HttpConnection) : org.jetbrains.ktor.cio.ReadChannel, HttpResponse {
    lateinit var stream: Stream

    private val headersBuilder = ValuesMapBuilder(caseInsensitiveKey = true)
    private val data = ArrayChannel<Pair<DataFrame, Callback>>(1)
    private val cf = CompletableFuture<Unit>()

    override val headers: ValuesMap
        get() = headersBuilder.build()

    override val status: HttpStatusCode
        get() = headers.get(":status").let { HttpStatusCode.fromValue(it!!.toInt()) }

    override val channel: ReadChannel
        get() = this

    suspend fun awaitStatus() {
        cf.await()
    }

    val Listener = object : Stream.Listener {
        override fun onPush(stream: Stream, frame: PushPromiseFrame): Stream.Listener {
            stream.reset(ResetFrame(frame.promisedStreamId, ErrorCode.CANCEL_STREAM_ERROR.code), Callback.NOOP)
            return Ignore
        }

        override fun onReset(stream: Stream, frame: ResetFrame) {
            println("onReset")
            if (frame.error == 0) data.close()
            else data.close(ClosedChannelException())

            cf.complete(Unit)
        }

        override fun onData(stream: Stream, frame: DataFrame, callback: Callback) {
            try {
                if (!data.offer(Pair(frame, callback))) IllegalStateException("data.offer() failed")
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

            cf.complete(Unit)
        }
    }

    suspend override fun read(dst: ByteBuffer): Int {
        val frame = data.poll()

        return if (frame != null) {
            readImpl(frame, dst)
        } else {
            readSuspend(dst)
        }
    }

    private suspend fun readSuspend(dst: ByteBuffer): Int {
        val framePair = data.receiveOrNull() ?: return -1
        return readImpl(framePair, dst)
    }

    private fun readImpl(framePair: Pair<DataFrame, Callback>, dst: ByteBuffer): Int {
        val buffer = framePair.first.data
        val rc = buffer.putTo(dst)

        if (buffer.hasRemaining()) {
            data.offer(framePair)
        } else {
            if (framePair.first.isEndStream) {
                data.close()
            }

            framePair.second.succeeded()
        }

        return rc
    }

    override fun close() {
        if (data.close()) {
            stream.reset(ResetFrame(stream.id, 0), Callback.NOOP)
        }
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