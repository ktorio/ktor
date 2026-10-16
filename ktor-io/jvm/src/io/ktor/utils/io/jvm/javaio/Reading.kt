/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.javaio

import io.ktor.utils.io.*
import io.ktor.utils.io.CloseToken.Companion.wrapCause
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.*
import kotlinx.io.Buffer
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

/**
 * Creates a [ByteReadChannel] that reads from this stream on demand in the given [context].
 * Reads **block** the executing thread. Use a dispatcher suitable for blocking I/O
 * in [context], such as [Dispatchers.IO] used by default.
 *
 * The channel closes the stream at EOF or on cancellation.
 * The [ByteReadChannel.cancel] call returns without waiting for stream closure.
 *
 * The stream must support closure during an active read.
 * Cancellation and closure do not guarantee that a blocking read stops.
 *
 * ### Structured concurrency
 *
 * If [context] contains a [Job], it closes the stream on cancellation.
 * If that job is active when the channel is created, it also waits for stream closure before completing.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.jvm.javaio.toByteReadChannel)
 */
@Suppress("UNUSED_PARAMETER")
public fun InputStream.toByteReadChannel(
    context: CoroutineContext = Dispatchers.IO,
    pool: ObjectPool<ByteBuffer>
): ByteReadChannel = RawSourceChannel(asSource(), context)

/**
 * Creates a [ByteReadChannel] that reads from this stream on demand in the given [context].
 * Reads **block** the executing thread. Use a dispatcher suitable for blocking I/O
 * in [context], such as [Dispatchers.IO] used by default.
 *
 * The channel closes the stream at EOF or on cancellation.
 * The [ByteReadChannel.cancel] call returns without waiting for stream closure.
 *
 * The stream must support closure during an active read.
 * Cancellation and closure do not guarantee that a blocking read stops.
 *
 * ### Structured concurrency
 *
 * If [context] contains a [Job], it closes the stream on cancellation.
 * If that job is active when the channel is created, it also waits for stream closure before completing.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.jvm.javaio.toByteReadChannel)
 */
@Suppress("UNUSED_PARAMETER")
@JvmName("toByteReadChannelWithArrayPool")
public fun InputStream.toByteReadChannel(
    context: CoroutineContext = Dispatchers.IO,
    pool: ObjectPool<ByteArray> = ByteArrayPool
): ByteReadChannel = RawSourceChannel(asSource(), context)

// Close must still run when blocking reads fill the normal IO dispatcher.
private val sourceCloseDispatcher = Dispatchers.IO.limitedParallelism(64)

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
                    source.readAtMostTo(buffer, Long.MAX_VALUE)
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
