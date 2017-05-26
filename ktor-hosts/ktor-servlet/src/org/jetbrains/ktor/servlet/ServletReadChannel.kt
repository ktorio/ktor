package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.cio.*
import java.io.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.atomic.*
import javax.servlet.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

class ServletReadChannel(val servletInputStream: ServletInputStream) : ReadChannel {
    private val listenerInstalled = AtomicBoolean()
    private val continuation = AtomicReference<Continuation<State>?>()

    @Volatile
    private var closed = false

    private enum class State {
        End,
        Available
    }

    private val readListener = object : ReadListener {
        override fun onAllDataRead() {
            continuation.getAndSet(null)?.resume(State.End)
        }

        override fun onError(t: Throwable) {
            continuation.getAndSet(null)?.resumeWithException(t)
        }

        override fun onDataAvailable() {
            continuation.getAndSet(null)?.resume(State.Available)
        }
    }

    suspend override fun read(dst: ByteBuffer): Int {
        if (!dst.hasRemaining()) {
            return 0
        }

        if (closed) {
            return -1
        }

        return when (awaitState()) {
            State.End -> -1
            State.Available -> try { servletInputStream.read(dst) } catch (end: EOFException) { -1 }
        }
    }

    private suspend fun awaitState(): State {
        if (listenerInstalled.compareAndSet(false, true)) {
            return awaitStateSlow()
        }

        if (servletInputStream.isFinished) return State.End

        return suspendCoroutineOrReturn { continuation ->
            if (!this.continuation.compareAndSet(null, continuation))
                throw IllegalStateException("Async operation is already in progress")

            if (servletInputStream.isReady) {
                this.continuation.set(null)
                State.Available
            } else COROUTINE_SUSPENDED
        }
    }

    private suspend fun awaitStateSlow(): State {
        val installed = suspendCoroutine<State> {
            if (!continuation.compareAndSet(null, it)) {
                listenerInstalled.set(false)
                it.resumeWithException(IllegalStateException("Async operation is already in progress"))
            } else
                servletInputStream.setReadListener(readListener)
        }

        if (installed != State.Available) return installed
        return awaitState()
    }

    override fun close() {
        try {
            listenerInstalled.set(true)
            closed = true
            servletInputStream.close()
        } finally {
            closed = true

            continuation.getAndSet(null)?.resumeWithException(ClosedChannelException())
        }
    }

    private fun ServletInputStream.read(buffer: ByteBuffer): Int {
        if (buffer.hasArray()) {
            val size = read(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
            if (size > 0) {
                buffer.position(buffer.position() + size)
            }
            return size
        } else {
            val tempArray = ByteArray(buffer.remaining())
            val size = read(tempArray)
            if (size > 0) {
                buffer.put(tempArray, 0, size)
            }
            return size
        }
    }
}