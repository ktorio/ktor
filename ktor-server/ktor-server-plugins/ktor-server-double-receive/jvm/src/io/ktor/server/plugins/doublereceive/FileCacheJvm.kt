/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import java.io.*
import java.nio.*

internal actual class FileCache actual constructor(
    private val bufferSize: Int
) : DoubleReceiveCache {

    private sealed interface State {
        /** The body is being written to the file. Readers wait for [done]. */
        class Saving(val done: CompletableDeferred<Unit>) : State

        /** The whole body is in the file. */
        object Saved : State

        class Failed(val cause: Throwable) : State
    }

    private val file = File.createTempFile("ktor-double-receive-cache", ".tmp")
    private val state = atomic<State>(initial = State.Saving(done = CompletableDeferred()))

    actual override fun CoroutineScope.launchPump(channel: ByteReadChannel): Job =
        launch(context = Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try {
                val buffer = ByteBuffer.allocate(bufferSize)

                FileOutputStream(file).use { stream ->
                    stream.channel.use { out ->
                        out.truncate(0L)

                        while (channel.readAvailable(buffer) != -1) {
                            if (state.value !is State.Saving) {
                                return@launch
                            }
                            buffer.flip()
                            while (buffer.hasRemaining()) {
                                out.write(buffer)
                            }
                            buffer.clear()
                        }
                    }
                }

                channel.closedCause?.let { throw it }
                transitionFromSaving(State.Saved, cause = null)
            } catch (cause: Throwable) {
                // Readers get the failure from the cache, so only cancellation is propagated to the caller.
                fail(cause)
                if (cause is CancellationException) throw cause
            }
        }

    actual override suspend fun reader(): ByteReadChannel {
        while (true) {
            when (val current = state.value) {
                is State.Saving -> current.done.await()
                State.Saved -> return file.readChannel()
                is State.Failed -> throw current.cause
            }
        }
    }

    actual override fun dispose() {
        val cause = IOException("The receive cache was disposed")
        val nextState = State.Failed(cause)
        while (true) {
            when (val current = state.value) {
                is State.Saving -> if (transitionFromSaving(nextState, cause)) break
                State.Saved -> if (state.compareAndSet(expect = current, update = nextState)) break
                is State.Failed -> return
            }
        }
        file.delete()
    }

    private fun fail(cause: Throwable) {
        val cause = cause.takeIf { it !is CancellationException } ?: IOException("Receiving was cancelled", cause)
        if (transitionFromSaving(State.Failed(cause), cause)) {
            file.delete()
        }
    }

    /**
     * Moves from [State.Saving] to [nextState] and resolves its `done`.
     * Returns `false` if the cache has already left [State.Saving].
     */
    private fun transitionFromSaving(nextState: State, cause: Throwable?): Boolean {
        while (true) {
            val current = state.value as? State.Saving ?: return false
            if (!state.compareAndSet(expect = current, update = nextState)) continue

            if (cause != null) {
                current.done.completeExceptionally(cause)
            } else {
                current.done.complete(Unit)
            }
            return true
        }
    }
}
