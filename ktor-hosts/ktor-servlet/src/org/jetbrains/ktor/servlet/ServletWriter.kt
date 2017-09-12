package org.jetbrains.ktor.servlet

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.sockets.*
import javax.servlet.*

internal fun servletWriter(output: ServletOutputStream): ReaderJob {
    val writer = ServletWriter(output)
    return reader(ioCoroutineDispatcher, writer.channel) {
        writer.run()
    }
}

private class ServletWriter(val output: ServletOutputStream) : WriteListener {
    val channel = ByteChannel()

    private val events = Channel<Unit>(2)
    private val buffer = ByteArray(8192)

    suspend fun run() {
        try {
            output.setWriteListener(this)
            events.receive()
            loop()
        } catch (t: Throwable) {
            onError(t)
        } finally {
            output.close()
        }
    }

    private suspend fun loop() {
        while (true) {
            val rc = channel.readAvailable(buffer)
            if (rc == -1) break
            copyLoop(rc)
        }
    }

    private suspend fun copyLoop(n: Int) {
        awaitReady()
        output.write(buffer, 0, n)
        awaitReady()
        output.flush()
    }

    private suspend fun awaitReady() {
        while (!output.isReady) {
            events.receive()
        }
    }

    override fun onWritePossible() {
        if (!events.offer(Unit)) {
            launch(Unconfined) {
                events.send(Unit)
            }
        }
    }

    override fun onError(t: Throwable) {
        events.close(t)
        channel.close(t)
    }
}