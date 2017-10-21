package io.ktor.server.jetty

import io.ktor.cio.*
import org.eclipse.jetty.io.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

internal class EndPointReadChannel(endp: EndPoint, exec: Executor) : AbstractConnection(endp, exec), ReadChannel, Connection.UpgradeTo {
    private val currentHandler = AtomicReference<Continuation<Int>>()
    private var currentBuffer: ByteBuffer? = null
    private val sync = Semaphore(0)

    suspend override fun read(dst: ByteBuffer): Int {
        if (!dst.hasRemaining()) return 0

        return suspendCoroutine { continuation ->
            if (!currentHandler.compareAndSet(null, continuation)) {
                throw IllegalStateException("Read operation is already in progress")
            }
            currentBuffer = dst

            sync.release()
            if (!isFillInterested) {
                fillInterested()
            } else {
                sync.release()
            }

            if (sync.tryAcquire(2)) {
                currentBuffer = null
                currentHandler.set(null)

                meet(dst, continuation)
            }
        }
    }

    override fun onIdleExpired() = false

    override fun onFillable() {
        sync.release()

        if (sync.tryAcquire(2)) {
            val bb = currentBuffer
            currentBuffer = null

            val handler = currentHandler.getAndSet(null)
            if (handler != null && bb != null) {
                meet(bb, handler)
            }
        }
    }

    override fun onFillInterestedFailed(cause: Throwable?) {
        super.onFillInterestedFailed(cause)

        val handler = currentHandler.getAndSet(null)
        if (handler != null) {
            currentBuffer = null

            if (cause is ClosedChannelException) {
                handler.resume(-1)
            } else {
                handler.resumeWithException(cause ?: Exception())
            }
        }
    }

    private fun meet(bb: ByteBuffer, handler: Continuation<Int>) {
        val delegate = bb.slice()
        delegate.limit(0)

        val rc = endPoint.fill(delegate)
        if (rc == -1) {
            handler.resume(-1)
        } else {
            bb.position(bb.position() + rc)
            handler.resume(rc)
        }
    }

    override fun onUpgradeTo(prefilled: ByteBuffer?) {
        if (prefilled != null && prefilled.hasRemaining()) {
            println("Got prefilled ${prefilled.remaining()} bytes")
            // TODO in theory client could try to start communication with no server upgrade acknowledge
            // it is generally not the case so it is not implemented yet
        }
    }
}