package org.jetbrains.ktor.nio

import java.nio.*
import java.util.concurrent.atomic.*

class AsyncSkipAndCut(val source: AsyncReadChannel, val skip: Long, val maxSize: Long, val preventClose: Boolean = false) : AsyncReadChannel {
    private val stateReference = AtomicReference(State.WAIT)

    private var skipBuffer: ByteBuffer? = ByteBuffer.allocate(8192)
    private var currentBuffer: ByteBuffer? = null
    private var currentHandler: AsyncHandler? = null

    @Volatile
    var skipped = 0L
        private set
    @Volatile
    var totalCount = 0L
        private set
    val state: State
        get() = stateReference.get()

    private val skipHandler = object : AsyncHandler {
        override fun success(count: Int) {
            require(count >= 0)

            skipped += count
            scheduleNextOperation()
        }

        override fun successEnd() {
            skipBuffer = null
            stateReference.compareAndSet(State.SKIP, State.READ)
            scheduleNextOperation()
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
            currentHandler?.successEnd()
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

    override fun releaseFlush() = source.releaseFlush()

    override fun close() {
        if (!preventClose) {
            source.close()
        }
    }

    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        if (stateReference.compareAndSet(State.WAIT_SKIPPED, State.READ) ||
                stateReference.compareAndSet(State.WAIT, State.SKIP) ||
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

    private fun scheduleNextOperation() {
        when (state) {
            State.SKIP -> skip()
            State.READ -> read()
            else -> Unit
        }
    }

    private fun skip() {
        if (skipped >= skip) {
            stateReference.set(State.READ)
            skipBuffer = null
            scheduleNextOperation()
            return
        }

        val buffer = skipBuffer!!
        buffer.clear()
        val remaining = skip - skipped
        if (remaining < buffer.capacity()) {
            buffer.limit(remaining.toInt())
        }

        source.read(buffer, skipHandler)
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

    private inline fun fireHandlerAndReset(block: (AsyncHandler) -> Unit) {
        val handler = currentHandler
        currentBuffer = null
        currentHandler = null

        if (handler != null) {
            block(handler)
        }
    }

    enum class State {
        WAIT,
        WAIT_SKIPPED,
        SKIP,
        READ,
        DONE
    }
}