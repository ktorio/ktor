package io.ktor.client.engine.jetty

import kotlinx.coroutines.experimental.io.*
import org.eclipse.jetty.http2.api.*
import org.eclipse.jetty.http2.frames.*
import org.eclipse.jetty.util.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

private val EMPTY_BUFFER = ByteBuffer.allocate(0)!!

internal class JettyHttp2Request(private val stream: Stream) : Callback {
    private val continuation = AtomicReference<Continuation<Unit>>()

    suspend fun write(src: ByteBuffer) = suspendCoroutine<Unit> { continuation ->
        this.continuation.set(continuation)
        val frame = DataFrame(stream.id, src, false)
        stream.data(frame, this)
    }

    override fun succeeded() {
        continuation.getAndSet(null)!!.resume(Unit)
    }

    override fun failed(t: Throwable) {
        continuation.getAndSet(null)!!.resumeWithException(t)
    }

    fun endBody() {
        stream.data(DataFrame(stream.id, EMPTY_BUFFER, true), Callback.NOOP)
    }
}
