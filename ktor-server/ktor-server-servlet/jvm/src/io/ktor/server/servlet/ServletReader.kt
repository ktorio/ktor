/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.servlet

import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import java.util.concurrent.*
import javax.servlet.*
import kotlin.time.Duration

internal fun CoroutineScope.servletReader(
    input: ServletInputStream,
    contentLength: Int,
    idleTimeout: Duration? = null
): WriterJob {
    val reader = ServletReader(input, contentLength, idleTimeout)

    return writer(Dispatchers.IO, reader.channel) {
        reader.run()
    }
}

private class ServletReader(
    val input: ServletInputStream,
    val contentLength: Int,
    val idleTimeout: Duration?
) : ReadListener {
    val channel = ByteChannel()
    private val events = Channel<Unit>(2)

    suspend fun run() {
        val buffer = ArrayPool.borrow()
        try {
            input.setReadListener(this)
            if (input.isFinished) {
                // setting read listener on already completed stream could cause it to hang
                // it is not by Servlet API spec, but it actually works like this
                // it is relatively dangerous to touch isFinished due to async processing
                // if the servlet container calls us onAllDataRead,
                // then we will close events again that is safe
                events.close()
                return
            }
            awaitEvent() ?: return
            loop(buffer)

            events.close()
            channel.close()
        } catch (cause: Throwable) {
            onError(cause)
        } finally {
            @Suppress("BlockingMethodInNonBlockingContext")
            input.close() // ServletInputStream is in non-blocking mode
            ArrayPool.recycle(buffer)
        }
    }

    private suspend fun awaitEvent(): Unit? =
        if (idleTimeout == null) {
            events.receiveCatching().getOrNull()
        } else {
            withTimeout(idleTimeout) {
                events.receiveCatching().getOrNull()
            }
        }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun loop(buffer: ByteArray) {
        var bodySize = 0
        while (true) {
            if (!input.isReady) {
                channel.flush()
                awaitEvent() ?: break
                continue
            }

            val readCount = input.read(buffer)
            if (readCount == -1) {
                events.close()
                break
            }

            bodySize += readCount

            channel.writeFully(buffer, 0, readCount)

            if (contentLength < 0) continue

            if (bodySize == contentLength) {
                channel.close()
                events.close()
                break
            }

            if (bodySize > contentLength) {
                val cause = IOException(
                    "Client provided more bytes than content length. Expected $contentLength but got $bodySize."
                )
                channel.close(cause)
                events.close()
                break
            }
        }
    }

    override fun onError(t: Throwable) {
        val wrappedException = wrapException(t)

        channel.cancel(wrappedException)
        events.close(wrappedException)
    }

    override fun onAllDataRead() {
        events.close()
    }

    override fun onDataAvailable() {
        try {
            if (!events.trySend(Unit).isSuccess) {
                events.trySendBlocking(Unit)
            }
        } catch (ignore: Throwable) {
        }
    }

    private fun wrapException(cause: Throwable): Throwable? {
        return when (cause) {
            is EOFException -> null
            is TimeoutException -> ChannelReadException(
                "Cannot read from a servlet input stream",
                exception = cause as Exception
            )
            else -> cause
        }
    }
}
