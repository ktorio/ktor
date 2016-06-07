package org.jetbrains.ktor.nio

import java.nio.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

private class AsyncPump(val bufferSize: Int = 8192, val from: AsyncReadChannel, val to: AsyncWriteChannel, val completionHandler: CompletableFuture<Long> = CompletableFuture(), val progressListener: ProgressListener<AsyncPump> = object: ProgressListener<AsyncPump> {
    override fun progress(source: AsyncPump) {
    }
}) {
    private val buffer = ByteBuffer.allocate(bufferSize)
    @Volatile
    private var bufferToWrite = false

    val state = AtomicReference(State.PAUSED)
    @Volatile
    var totalCount = 0L
        private set

    private val readHandler = object : AsyncHandler {
        override fun success(count: Int) {
            require(count >= 0)

            if (count > 0) {
                buffer.flip()
                bufferToWrite = true
                write()
            } else {
                read()
            }
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

    private val writeHandler = object : AsyncHandler {
        override fun success(count: Int) {
            require(count >= 0)

            if (count > 0) {
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
            completionHandler.completeExceptionally(cause)
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
        }
    }

    enum class State {
        PAUSED,
        RUNNING,
        DONE
    }
}

fun AsyncReadChannel.copyToAsync(out: AsyncWriteChannel) {
    AsyncPump(from = this, to = out).start()
}

fun AsyncReadChannel.copyToAsyncThenComplete(out: AsyncWriteChannel, completableFuture: CompletableFuture<Long>) {
    AsyncPump(from = this, to = out, completionHandler = completableFuture).start()
}