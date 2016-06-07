package org.jetbrains.ktor.nio

import java.nio.*
import java.util.concurrent.atomic.*

class AsyncSeekAndCut(val source: SeekableAsyncChannel, val seek: Long, val maxSize: Long, val preventClose: Boolean = false) : SeekableAsyncChannel {
    private val stateReference = AtomicReference(State.WAIT)

    private var currentBuffer: ByteBuffer? = null
    private var currentHandler: AsyncHandler? = null
    private var localSeek = 0L

    @Volatile
    var totalCount = 0L
        private set
    val state: State
        get() = stateReference.get()

    private val seekHandler = object : AsyncHandler {
        override fun success(count: Int) {
        }

        override fun successEnd() {
            stateReference.compareAndSet(State.SEEK, State.READ)
            if (currentBuffer == null) {
                fireHandlerAndReset { it.successEnd() }
            } else {
                scheduleNextOperation()
            }
        }

        override fun failed(cause: Throwable) {
            stateReference.set(State.DONE)
            fireHandlerAndReset { it.failed(cause) }
        }
    }
    private val readHandler = object : AsyncHandler {
        override fun success(count: Int) {
            require(count >= 0)

            totalCount += count
            fireHandlerAndReset { it.success(count) }
        }

        override fun successEnd() {
            stateReference.compareAndSet(State.READ, State.DONE)
            fireHandlerAndReset { it.successEnd() }
        }

        override fun failed(cause: Throwable) {
            stateReference.set(State.DONE)
            fireHandlerAndReset { it.failed(cause) }
        }
    }
    private val tailReadHandler = object : AsyncHandler by readHandler {
        override fun success(count: Int) {
            if (count > 0) {
                currentBuffer?.position(currentBuffer!!.position() + count)
            }
            readHandler.success(count)
        }
    }

    override fun close() {
        if (!preventClose) {
            source.close()
        }
    }

    override val position: Long
        get() = localSeek

    override fun seek(position: Long, handler: AsyncHandler) {
        if (stateReference.compareAndSet(State.WAIT, State.SEEK) ||
                stateReference.compareAndSet(State.WAIT_SEEKED, State.SEEK)) {
            localSeek = position
            currentHandler = handler
            scheduleNextOperation()
        } else {
            handler.failed(IllegalStateException("Wrong state: $state"))
        }
    }

    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        if (stateReference.compareAndSet(State.WAIT_SEEKED, State.READ) ||
                stateReference.compareAndSet(State.WAIT, State.SEEK) ||
                stateReference.get() == State.READ) {
            currentBuffer = dst
            currentHandler = handler
            scheduleNextOperation()
        } else if (stateReference.get() == State.DONE) {
            handler.successEnd()
        } else {
            handler.failed(IllegalStateException("Wrong state: $state"))
        }
    }

    private inline fun fireHandlerAndReset(block: (AsyncHandler) -> Unit) {
        val handler = currentHandler
        currentBuffer = null
        currentHandler = null

        if (handler != null) {
            block(handler)
        }
    }

    private fun scheduleNextOperation() {
        when (stateReference.get()) {
            State.SEEK -> source.seek(seek + localSeek, seekHandler)
            State.READ -> read()
            else -> Unit
        }
    }

    private fun read() {
        if (totalCount == maxSize) {
            stateReference.set(State.DONE)
            fireHandlerAndReset { it.successEnd() }
            return
        }

        val buffer = currentBuffer!!
        val remaining = maxSize - totalCount
        if (remaining < buffer.remaining()) {
            val wrapped = buffer.slice()
            wrapped.limit(remaining.toInt())
            source.read(wrapped, tailReadHandler)
        } else {
            source.read(buffer, readHandler)
        }
    }

    enum class State {
        WAIT,
        WAIT_SEEKED,
        SEEK,
        READ,
        DONE
    }
}