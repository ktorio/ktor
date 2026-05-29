/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.javaio

import io.ktor.utils.io.*
import io.ktor.utils.io.CloseToken.Companion.wrapCause
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.*
import kotlinx.io.Buffer
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

/**
 * Open a channel and launch a coroutine to copy bytes from the input stream to the channel.
 * Please note that it may block your async code when started on [Dispatchers.Unconfined]
 * since [InputStream] is blocking on it's nature
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.jvm.javaio.toByteReadChannel)
 */
@Suppress("UNUSED_PARAMETER")
public fun InputStream.toByteReadChannel(
    context: CoroutineContext = Dispatchers.IO,
    pool: ObjectPool<ByteBuffer>
): ByteReadChannel = RawSourceChannel(asSource(), context)

/**
 * Open a channel and launch a coroutine to copy bytes from the input stream to the channel.
 * Please note that it may block your async code when started on [Dispatchers.Unconfined]
 * since [InputStream] is blocking on it's nature
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.jvm.javaio.toByteReadChannel)
 */
@Suppress("UNUSED_PARAMETER")
@JvmName("toByteReadChannelWithArrayPool")
public fun InputStream.toByteReadChannel(
    context: CoroutineContext = Dispatchers.IO,
    pool: ObjectPool<ByteArray> = ByteArrayPool
): ByteReadChannel = RawSourceChannel(asSource(), context)

internal class RawSourceChannel(
    private val source: RawSource,
    parent: CoroutineContext
) : ByteReadChannel {
    private var closedToken: CloseToken? = null
    private val buffer = Buffer()

    override val closedCause: Throwable?
        get() = closedToken?.wrapCause()

    override val isClosedForRead: Boolean
        get() = closedToken != null && buffer.exhausted()

    val job = Job(parent[Job])
    val coroutineContext = parent + job + CoroutineName("RawSourceChannel")

    @InternalAPI
    override val readBuffer: Source
        get() = buffer

    init {
        @OptIn(InternalCoroutinesApi::class)
        job.invokeOnCompletion(onCancelling = true) { cause ->
            if (cause != null) closeSource(cause.asCancellationException())
        }
    }

    @OptIn(InternalAPI::class)
    override suspend fun awaitContent(min: Int): Boolean {
        if (closedToken != null) {
            rethrowCloseCauseIfNeeded()
            return buffer.remaining >= min
        }

        withContext(coroutineContext) {
            var result = 0L
            while (buffer.remaining < min && result >= 0) {
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
                source.close()
                job.complete()
                rethrowCloseCauseIfNeeded()
                closedToken = CLOSED
            }
        }

        return buffer.remaining >= min
    }

    override fun cancel(cause: Throwable?) {
        if (closedToken != null) return
        val cause = cause?.asCancellationException()
        job.cancel(cause)
        closeSource(cause)
    }

    private fun closeSource(cause: CancellationException?) {
        if (closedToken != null) return
        closedToken = CloseToken(cause)
        source.close()
    }

    private fun Throwable.asCancellationException(): CancellationException =
        this as? CancellationException ?: CancellationException(message ?: "Channel was cancelled", this)
}
