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
            if (servletInputStream.isReady) {
                continuation.getAndSet(null)?.resume(State.Available)
            }
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
        return when {
            listenerInstalled.compareAndSet(false, true) -> awaitStateInstall()
            servletInputStream.isFinished -> return State.End
            servletInputStream.isReady -> return State.Available
            else -> {
                suspendCoroutineOrReturn<State> { c ->
                    when {
                        !this.continuation.compareAndSet(null, c) -> throw IllegalStateException("Async operation is already in progress")
                        servletInputStream.isFinished -> {
                            this.continuation.set(null)
                            State.End
                        }
                        servletInputStream.isReady -> {
                            this.continuation.set(null)
                            State.Available
                        }
                        else -> COROUTINE_SUSPENDED
                    }
                }
            }
        }

    }

    private suspend fun awaitStateInstall(): State {
        return suspendCoroutineOrReturn<State> { c ->
            if (!this.continuation.compareAndSet(null, c)) {
                throw IllegalStateException("Listener installation failed: already in progress")
            }

            servletInputStream.setReadListener(readListener)
            COROUTINE_SUSPENDED
        }
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