/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.CloseToken.Companion.wrapCause
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.*
import kotlinx.io.Buffer
import kotlin.coroutines.CoroutineContext

// Close must still run when blocking reads fill the normal IO dispatcher.
private val sourceCloseDispatcher = ioDispatcher().limitedParallelism(64)

internal class RawSourceChannel(
    private val source: RawSource,
    parent: CoroutineContext
) : ByteReadChannel {
    private val closedToken = atomic<CloseToken?>(null)

    private val buffer = Buffer()

    override val closedCause: Throwable?
        get() = closedToken.value?.wrapCause()

    override val isClosedForRead: Boolean
        get() = closedToken.value != null && buffer.exhausted()

    val job = Job(parent[Job])
    val coroutineContext = parent + job + CoroutineName("RawSourceChannel")

    @InternalAPI
    override val readBuffer: Source
        get() = buffer

    init {
        @OptIn(InternalCoroutinesApi::class)
        job.invokeOnCompletion(onCancelling = true) { cause ->
            if (cause != null) {
                closeSourceOnCancellation(cause.asCancellationException())
            }
        }
    }

    @OptIn(InternalAPI::class)
    override suspend fun awaitContent(min: Int): Boolean {
        if (closedToken.value != null) {
            rethrowCloseCauseIfNeeded()
            return buffer.remaining >= min
        }

        withContext(coroutineContext) {
            var result = 0L
            while (buffer.remaining < min && result >= 0) {
                ensureActive()
                result = try {
                    source.readAtMostTo(buffer, CHANNEL_MAX_SIZE.toLong())
                } catch (_: EOFException) {
                    -1L
                } catch (cause: IOException) {
                    rethrowCloseCauseIfNeeded()
                    throw cause
                }
            }

            if (result == -1L) {
                closeNormally()
                rethrowCloseCauseIfNeeded()
            }
        }

        return buffer.remaining >= min
    }

    private fun closeNormally() {
        if (!closedToken.compareAndSet(null, CLOSED)) return

        try {
            source.close()
        } catch (cause: Throwable) {
            closedToken.value = CloseToken(cause)
            throw cause
        } finally {
            job.complete()
        }
    }

    override fun cancel(cause: Throwable?) {
        if (closedToken.value != null) return
        val cancellation = cause.asCancellationException()
        job.cancel(cancellation)
        closeSourceOnCancellation(cancellation)
    }

    private fun closeSourceOnCancellation(cause: CancellationException) {
        if (!closedToken.compareAndSet(null, CloseToken(cause))) return

        // Keep cleanup under the channel job. ATOMIC starts it even when that job is already cancelled.
        CoroutineScope(coroutineContext + sourceCloseDispatcher).launch(start = CoroutineStart.ATOMIC) {
            runCatching { source.close() }
                .onFailure { cause.addSuppressed(it) }
        }
    }

    private fun Throwable?.asCancellationException(): CancellationException =
        this as? CancellationException ?: CancellationException(this?.message ?: "Channel was cancelled", this)
}
