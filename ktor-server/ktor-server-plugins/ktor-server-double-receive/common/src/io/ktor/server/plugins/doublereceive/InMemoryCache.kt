/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.IOException

internal class InMemoryCache : DoubleReceiveCache {

    internal sealed interface State {
        /**
         * The body is arriving. It's buffered and forwarded to [channel], which is handed to the first receive.
         * A later receive cancels [channel] and waits for [done].
         */
        class Receiving(
            val channel: ByteChannel,
            val done: CompletableDeferred<Unit>,
            val isChannelTaken: Boolean
        ) : State

        /** The whole buffer is in memory. */
        class Cached(val buffer: Buffer) : State

        class Failed(val cause: Throwable) : State
    }

    internal val state = atomic<State>(
        initial = State.Receiving(
            channel = ByteChannel(autoFlush = true),
            done = CompletableDeferred(),
            isChannelTaken = false
        )
    )

    override suspend fun reader(): ByteReadChannel {
        while (true) {
            when (val current = state.value) {
                is State.Receiving -> {
                    if (current.isChannelTaken) {
                        // Receives are sequential, so the previous one is finished or abandoned
                        current.channel.cancel()
                        current.done.await()
                        continue
                    }
                    val taken = State.Receiving(current.channel, current.done, isChannelTaken = true)
                    if (state.compareAndSet(expect = current, update = taken)) return current.channel
                }

                is State.Cached -> return ByteReadChannel(source = current.buffer.peek())

                is State.Failed -> throw current.cause
            }
        }
    }

    override fun dispose() {
        val cause = IOException("The receive cache was disposed")
        val nextState = State.Failed(cause)
        while (true) {
            when (val current = state.value) {
                is State.Receiving -> {
                    val channel = transitionFromReceiving(nextState, cause) ?: continue
                    channel.cancel()
                    return
                }

                is State.Cached -> {
                    if (!state.compareAndSet(expect = current, update = nextState)) continue
                    current.buffer.clear()
                    return
                }

                is State.Failed -> return
            }
        }
    }

    private fun complete(cacheBuffer: Buffer) {
        val channel = transitionFromReceiving(State.Cached(cacheBuffer), cause = null)
        if (channel == null) {
            cacheBuffer.clear()
            return
        }
        channel.close()
    }

    private fun fail(cause: Throwable) {
        val cause = cause.takeIf { it !is CancellationException } ?: IOException("Receiving was cancelled", cause)
        transitionFromReceiving(State.Failed(cause), cause)?.cancel(cause)
    }

    /**
     * Moves from [State.Receiving] to [nextState] and resolves its `done`.
     * Returns the receive channel, or `null` if the cache has already left [State.Receiving].
     */
    private fun transitionFromReceiving(nextState: State, cause: Throwable?): ByteChannel? {
        while (true) {
            val current = state.value as? State.Receiving ?: return null
            if (!state.compareAndSet(expect = current, update = nextState)) continue

            if (cause != null) {
                current.done.completeExceptionally(cause)
            } else {
                current.done.complete(Unit)
            }
            return current.channel
        }
    }

    override fun CoroutineScope.launchPump(channel: ByteReadChannel) =
        launch(start = CoroutineStart.UNDISPATCHED) {
            val buffer = ByteArrayPool.borrow()
            val cacheBuffer = Buffer()
            try {
                // Set once the receive channel is closed by its reader; the body is then only cached
                var isForwarding = true
                while (true) {
                    val size = channel.readAvailable(buffer)
                    if (size == -1) break

                    val current = state.value as? State.Receiving ?: return@launch
                    cacheBuffer.writeFully(buffer, offset = 0, length = size)
                    if (!isForwarding) continue
                    try {
                        current.channel.writeFully(buffer, startIndex = 0, endIndex = size)
                    } catch (_: ClosedWriteChannelException) {
                        isForwarding = false
                    }
                }
                channel.closedCause?.let { throw it }
                complete(cacheBuffer)
            } catch (cause: Throwable) {
                // Readers get the failure from the cache, so only cancellation is propagated to the caller.
                cacheBuffer.clear()
                fail(cause)
                if (cause is CancellationException) throw cause
            } finally {
                ByteArrayPool.recycle(buffer)
            }
        }
}
