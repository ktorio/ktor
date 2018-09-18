package io.ktor.server.servlet

import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.io.*
import java.io.*
import java.util.concurrent.TimeoutException
import javax.servlet.*
import kotlin.coroutines.*

internal fun servletReader(input: ServletInputStream, parent: CoroutineContext? = null): WriterJob {
    val reader = ServletReader(input)

    return writer(if (parent != null) Unconfined + parent else Unconfined, reader.channel) {
        reader.run()
    }
}

private class ServletReader(val input: ServletInputStream) : ReadListener {
    val channel = ByteChannel()
    private val events = Channel<Unit>(2)

    suspend fun run() {
        val buffer = ArrayPool.borrow()
        try {
            input.setReadListener(this)
            events.receiveOrNull() ?: return
            loop(buffer)

            events.close()
            channel.close()
        } catch (t: Throwable) {
            onError(t)
        } finally {
            input.close()
            ArrayPool.recycle(buffer)
        }
    }

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
                runBlocking(Unconfined) {
                    events.send(Unit)
                }
            }
        } catch (ignore: Throwable) {
        }
    }

    private fun wrapException(t: Throwable): Throwable? {
        return when (t) {
            is EOFException -> null
            is TimeoutException,
            is IOException -> ChannelReadException("Cannot read from a servlet input stream", exception = t as Exception)
            else -> t
        }
    }
}