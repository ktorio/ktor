package io.ktor.client.engine.jetty

import kotlinx.coroutines.*
import org.eclipse.jetty.http2.api.*
import org.eclipse.jetty.http2.frames.*
import org.eclipse.jetty.util.*
import java.nio.*
import java.util.concurrent.atomic.*

private val EmptyByteBuffer = ByteBuffer.allocate(0)!!

internal class JettyHttp2Request(private val stream: Stream) : Callback {
    private val deferred = AtomicReference<CompletableDeferred<Unit>?>()

    suspend fun write(src: ByteBuffer) {
        val result = CompletableDeferred<Unit>()
        deferred.set(result)

        val frame = DataFrame(stream.id, src, false)
        stream.data(frame, this)

        result.await()
    }

    override fun succeeded() {
        deferred.getAndSet(null)!!.complete(Unit)
    }

    override fun failed(cause: Throwable) {
        deferred.getAndSet(null)!!.completeExceptionally(cause)
    }

    fun endBody() {
        stream.data(DataFrame(stream.id, EmptyByteBuffer, true), Callback.NOOP)
    }
}
