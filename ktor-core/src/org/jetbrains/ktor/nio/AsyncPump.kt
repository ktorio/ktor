package org.jetbrains.ktor.nio

import java.nio.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class AsyncPump(bufferSize: Int = 8192, val from: ReadChannel, val to: WriteChannel, val completionHandler: CompletableFuture<Long> = CompletableFuture(), val progressListener: ProgressListener<AsyncPump> = object: ProgressListener<AsyncPump> {
    override fun progress(source: AsyncPump) {
    }
}, ignoreWriteError: Boolean = false) {
    private val buffer = ByteBuffer.allocate(bufferSize)
    @Volatile
    private var bufferToWrite = false

    val state: AtomicReference<State> = AtomicReference(State.PAUSED)
    @Volatile
    var totalCount = 0L
        private set

    private val flushHandler = object : AsyncHandler {
        override fun success(count: Int) {
        }

        override fun successEnd() {
            state.set(State.DONE)
            completionHandler.complete(totalCount)
        }

        override fun failed(cause: Throwable) {
            state.set(State.DONE)
            completionHandler.completeExceptionally(cause)
        }
    }

    private val readHandler = object : AsyncHandler {
        override fun success(count: Int) {
            require(count >= 0)

            if (count > 0 || !buffer.hasRemaining()) {
                buffer.flip()
                bufferToWrite = true
                write()
            } else {
                read()
            }
        }

        override fun successEnd() {
            tailFlush()
        }

        override fun failed(cause: Throwable) {
            state.set(State.DONE)
            completionHandler.completeExceptionally(cause)
        }
    }

    private val writeHandler = object : AsyncHandler {
        override fun success(count: Int) {
            require(count >= 0)

            if (count > 0 || !buffer.hasRemaining()) {
                totalCount += count
                progressListener.progress(this@AsyncPump)

                buffer.compact()
                bufferToWrite = false

                read()
            } else {
                write()
            }
        }

        override fun successEnd() {
        }

        override fun failed(cause: Throwable) {
            state.set(State.DONE)

            when (ignoreWriteError) {
                true -> completionHandler.complete(totalCount)
                false -> completionHandler.completeExceptionally(cause)
            }
        }
    }

    fun start() {
        resume()
    }

    fun pause() {
        state.compareAndSet(State.RUNNING, State.PAUSED)
    }

    fun resume() {
        if (state.compareAndSet(State.PAUSED, State.RUNNING)) {
            if (bufferToWrite) {
                write()
            } else {
                read()
            }
        }
    }

    private fun read() {
        if (state.get() == State.RUNNING) {
            from.read(buffer, readHandler)
        }
    }

    private fun write() {
        if (state.get() == State.RUNNING) {
            to.write(buffer, writeHandler)

            flush()
        }
    }

    private fun flush() {
        if (state.get() == State.RUNNING && from.releaseFlush() > 0) {
            to.requestFlush()
        }
    }

    private fun tailFlush() {
        if (state.get() == State.RUNNING) {
            from.releaseFlush()
            to.flush(flushHandler)
        }
    }

    enum class State {
        PAUSED,
        RUNNING,
        DONE
    }
}

fun ReadChannel.copyToAsync(out: WriteChannel, ignoreWriteError: Boolean = false) {
    AsyncPump(from = this, to = out, ignoreWriteError = ignoreWriteError).start()
}

fun ReadChannel.copyToAsyncThenComplete(out: WriteChannel, completableFuture: CompletableFuture<Long>, ignoreWriteError: Boolean = false) {
    AsyncPump(from = this, to = out, completionHandler = completableFuture, ignoreWriteError = ignoreWriteError).start()
}