/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servlet.jakarta

import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import jakarta.servlet.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import java.util.concurrent.*

@Suppress("DEPRECATION")
internal fun CoroutineScope.servletWriter(output: ServletOutputStream): ReaderJob {
    val writer = ServletWriter(output)
    return reader(Dispatchers.IO, writer.channel) {
        writer.run()
    }
}

internal val ArrayPool = object : DefaultPool<ByteArray>(1024) {
    override fun produceInstance() = ByteArray(4096)
    override fun validateInstance(instance: ByteArray) {
        if (instance.size != 4096) {
            throw IllegalArgumentException(
                "Tried to recycle wrong ByteArray instance: most likely it hasn't been borrowed from this pool"
            )
        }
    }
}

private class ServletWriter(val output: ServletOutputStream) : WriteListener {
    val channel = ByteChannel()

    private val finishedEvent = Channel<Unit>(2)

    suspend fun run() {
        output.setWriteListener(this)
        finishedEvent.receive()
    }

    override fun onWritePossible() = runBlocking {
        val buffer = ArrayPool.borrow()
        while (output.isReady) {
            val rc = channel.readAvailable(buffer)
            if (rc == -1) {
                finishedEvent.trySendBlocking(Unit)
                return@runBlocking
            }

            output.write(buffer, 0, rc)
        }
        // we shouldn't recycle it in finally
        // because in case of error the buffer could be still hold by servlet container
        // so we simply drop it as buffer leak has only limited performance impact
        // (buffer will be collected by GC and pool will produce another one)
        ArrayPool.recycle(buffer)
    }

    override fun onError(t: Throwable) {
        val wrapped = wrapException(t)
        finishedEvent.close(wrapped)
        channel.cancel(wrapped)
    }

    private fun wrapException(cause: Throwable): Throwable {
        return if (cause is IOException || cause is TimeoutException) {
            ChannelWriteException("Failed to write to servlet async stream", exception = cause)
        } else {
            cause
        }
    }
}
