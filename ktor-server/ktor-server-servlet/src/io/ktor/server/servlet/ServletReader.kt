package io.ktor.server.servlet

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import javax.servlet.*

internal fun servletReader(input: ServletInputStream): WriterJob {
    val reader = Reader(input)

    return writer(Unconfined, reader.channel) {
        reader.run()
    }
}

private class Reader(val input: ServletInputStream) : ReadListener {
    val channel = ByteChannel()
    private val events = Channel<Unit>(2)

    suspend fun run() {
        val buffer = ArrayPool.borrow()
        try {
            input.setReadListener(this)
            events.receiveOrNull() ?: return
            loop(buffer)
        } catch (eof: EOFException) {
        } catch (cancelled: CancellationException) {
        } catch (t: Throwable) {
            onError(t)
        } finally {
            channel.close()
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
        channel.close(t)
        events.close(t)
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
}