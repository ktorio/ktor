package org.jetbrains.ktor.servlet

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.sockets.*
import kotlinx.sockets.impl.*
import javax.servlet.*

internal fun servletWriter(output: ServletOutputStream): ReaderJob {
    val writer = ServletWriter(output)
    return reader(ioCoroutineDispatcher, writer.channel) {
        writer.run()
    }
}

internal val ArrayPool = object : ObjectPoolImpl<ByteArray>(1024) {
    override fun produceInstance() = ByteArray(4096)
    override fun validateInstance(instance: ByteArray) {
        if (instance.size != 4096) throw IllegalArgumentException("Tried to recycle wrong ByteArray instance: most likely it hasn't been borrowed from this pool")
    }
}

private class ServletWriter(val output: ServletOutputStream) : WriteListener {
    val channel = ByteChannel()

    private val events = Channel<Unit>(2)

    suspend fun run() {
        val buffer = ArrayPool.borrow()
        try {
            output.setWriteListener(this)
            events.receive()
            loop(buffer)

            finish()
        } catch (t: Throwable) {
            onError(t)
        } finally {
            output.close()
            ArrayPool.recycle(buffer)
        }
    }

    private suspend fun finish() {
        output.flush()
        awaitReady()
    }

    private suspend fun loop(buffer: ByteArray) {
        while (true) {
            val rc = channel.readAvailable(buffer)
            if (rc == -1) break
            copyLoop(buffer, rc)
        }
    }

    private suspend fun copyLoop(buffer: ByteArray, n: Int) {
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