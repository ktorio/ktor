/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.servlet

import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import java.util.concurrent.TimeoutException
import javax.servlet.*

internal fun CoroutineScope.servletReader(input: ServletInputStream): WriterJob {
    val reader = ServletReader(input)

    return writer(Dispatchers.Unconfined, reader.channel) {
        reader.run()
    }
}

private class ServletReader(val input: ServletInputStream) : ReadListener {
    val channel = ByteChannel()
    private val events = Channel<Unit>(2)

    public suspend fun run() {
        val buffer = ArrayPool.borrow()
        try {
            input.setReadListener(this)
            if (input.isFinished) {
                // setting read listener on already completed stream could cause it to hang
                // it is not by Servlet API spec but it actually works like this
                // it is relatively dangerous to touch isFinished due to async processing
                // if the servlet container call us onAllDataRead then it we will close events again that is safe
                events.close()
                return
            }
            @OptIn(ExperimentalCoroutinesApi::class)
            events.receiveOrNull() ?: return
            loop(buffer)

            events.close()
            channel.close()
        } catch (t: Throwable) {
            onError(t)
        } finally {
            @Suppress("BlockingMethodInNonBlockingContext")
            input.close() // ServletInputStream is in non-blocking mode
            ArrayPool.recycle(buffer)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun loop(buffer: ByteArray) {
        while (true) {
            if (input.isReady) {
                val rc = input.read(buffer)
                if (rc == -1) {
                    events.close()
                    break
                }

                channel.writeFully(buffer, 0, rc)
            } else {
                channel.flush()
                @OptIn(ExperimentalCoroutinesApi::class)
                events.receiveOrNull() ?: break
            }
        }
    }

    override fun onError(t: Throwable) {
        val wrappedException = wrapException(t)

        channel.close(wrappedException)
        events.close(wrappedException)
    }

    override fun onAllDataRead() {
        events.close()
    }

    override fun onDataAvailable() {
        try {
            if (!events.offer(Unit)) {
                events.sendBlocking(Unit)
            }
        } catch (ignore: Throwable) {
        }
    }

    private fun wrapException(t: Throwable): Throwable? {
        return when (t) {
            is EOFException -> null
            is TimeoutException,
            is IOException -> ChannelReadException(
                "Cannot read from a servlet input stream",
                exception = t as Exception
            )
            else -> t
        }
    }
}
