/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.server.request.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlinx.io.Buffer
import kotlin.coroutines.*

internal class MemoryCache(
    val body: ByteReadChannel,
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) : DoubleReceiveCache {
    private val stateMutex = Mutex()
    private val fullBody = CompletableDeferred<Buffer>()
    private var activeReader: ByteReadChannel? = null

    @OptIn(DelicateCoroutinesApi::class)
    private val readerJob = GlobalScope.writer(coroutineContext, ByteChannel(autoFlush = true)) {
        val buffer = ByteArrayPool.borrow()
        val packet = Buffer()
        try {
            while (!body.isClosedForRead) {
                val size = body.readAvailable(buffer)
                if (size == -1) break
                packet.writeFully(buffer, 0, size)

                channel.writeFully(buffer, 0, size)
            }

            body.closedCause?.let { throw it }
            if (!fullBody.complete(packet)) {
                packet.discard()
            }
        } catch (cause: Throwable) {
            packet.discard()
            fullBody.completeExceptionally(cause)
            throw cause
        } finally {
            ByteArrayPool.recycle(buffer)
        }
    }

    init {
        readerJob.job.invokeOnCompletion { cause ->
            if (cause != null && fullBody.completeExceptionally(cause)) {
                body.cancel(cause)
            }
        }
    }

    private val reader: ByteReadChannel = object : ByteReadChannel by readerJob.channel {
        override fun cancel(cause: Throwable?) {
            val failure = cause ?: CancellationException("The first cache reader was cancelled", null)
            if (fullBody.completeExceptionally(failure)) {
                body.cancel(cause = failure)
                readerJob.job.cancel()
            }
            readerJob.channel.cancel(cause = failure)
        }
    }

    override suspend fun read(): ByteReadChannel = stateMutex.withLock {
        if (activeReader?.isOpenForRead() == true) {
            throw RequestAlreadyConsumedException()
        }

        val nextReader = if (activeReader == null) {
            reader
        } else {
            ByteReadChannel(source = fullBody.await().peek())
        }
        activeReader = nextReader
        nextReader
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun dispose() {
        val cancellation = CancellationException("The receive cache was disposed", null)
        activeReader?.cancel(cancellation)
        if (fullBody.completeExceptionally(cancellation)) {
            body.cancel(cancellation)
            readerJob.channel.cancel(cancellation)
            readerJob.job.cancel()
        }
        GlobalScope.launch {
            val packet = try {
                fullBody.await()
            } catch (_: kotlinx.coroutines.CancellationException) {
                currentCoroutineContext().ensureActive()
                return@launch
            } catch (_: Throwable) {
                return@launch
            }
            stateMutex.withLock { packet.discard() }
        }
    }
}

private fun ByteReadChannel.isOpenForRead(): Boolean = runCatching { !isClosedForRead }.getOrDefault(false)
