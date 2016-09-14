package org.jetbrains.ktor.jetty

import org.eclipse.jetty.io.*
import org.jetbrains.ktor.nio.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

internal class AbstractConnectionReadChannel(endp: EndPoint, exec: Executor) : AbstractConnection(endp, exec), ReadChannel, Connection.UpgradeTo {
    private val currentHandler = AtomicReference<AsyncHandler>()
    private var currentBuffer: ByteBuffer? = null
    private val sync = Semaphore(0)

    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        if (!currentHandler.compareAndSet(null, handler)) {
            throw IllegalStateException("Read operation is already in progress")
        }
        currentBuffer = dst

        if (!dst.hasRemaining()) {
            currentBuffer = null
            currentHandler.set(null)

            handler.success(0)
            return
        }

        sync.release()
        if (!isFillInterested) {
            fillInterested()
        } else {
            sync.release()
        }

        if (sync.tryAcquire(2)) {
            currentBuffer = null
            currentHandler.set(null)

            meet(dst, handler)
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
                handler.successEnd()
            } else {
                handler.failed(cause ?: Exception())
            }
        }
    }

    private fun meet(bb: ByteBuffer, handler: AsyncHandler) {
        val delegate = bb.slice()
        delegate.limit(0)

        val rc = endPoint.fill(delegate)
        if (rc == -1) {
            handler.successEnd()
        } else if (rc == 0) {
            read(bb, handler)
        } else {
            bb.position(bb.position() + rc)
            handler.success(rc)
        }
    }

    override fun onUpgradeTo(prefilled: ByteBuffer?) {
        // TODO use prefilled buffer
        // println("Got prefilled ${prefilled?.remaining()} bytes")
    }
}