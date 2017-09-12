package org.jetbrains.ktor.servlet

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.sockets.*
import javax.servlet.*

internal fun servletReader(input: ServletInputStream): WriterJob {
    val reader = Reader(input)

    return writer(ioCoroutineDispatcher, reader.channel) {
        reader.run()
    }
}

private class Reader(val input: ServletInputStream) : ReadListener {
    val channel = ByteChannel()
    private val events = Channel<Unit>(2)
    private val buffer = ByteArray(8192)

    suspend fun run() {
        try {
            input.setReadListener(this)
            events.receiveOrNull() ?: return
            loop()
        } catch (t: Throwable) {
            onError(t)
        } finally {
            channel.close()
            input.close()
        }
    }

    private suspend fun loop() {
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
        if (!events.offer(Unit)) {
            runBlocking(Unconfined) {
                events.send(Unit)
            }
        }
    }
}