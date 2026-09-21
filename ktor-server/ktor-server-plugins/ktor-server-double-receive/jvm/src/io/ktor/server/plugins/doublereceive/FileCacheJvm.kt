/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.server.request.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import java.io.*
import java.nio.*
import kotlin.coroutines.*

internal actual class FileCache actual constructor(
    private val body: ByteReadChannel,
    bufferSize: Int,
    context: CoroutineContext
) : DoubleReceiveCache {
    private val stateMutex = Mutex()
    private val file = File.createTempFile("ktor-double-receive-cache", ".tmp")
    private var activeReader: ByteReadChannel? = null

    @OptIn(DelicateCoroutinesApi::class)
    private val saveJob = GlobalScope.async(context + Dispatchers.IO) {
        val buffer = ByteBuffer.allocate(bufferSize)

        FileOutputStream(file).use { stream ->
            stream.channel.use { out ->
                out.truncate(0L)
                buffer.position(buffer.limit())

                while (true) {
                    while (buffer.hasRemaining()) {
                        out.write(buffer)
                    }
                    buffer.clear()

                    if (body.readAvailable(buffer) == -1) break
                    buffer.flip()
                }
            }
        }
    }

    init {
        saveJob.invokeOnCompletion { cause ->
            if (cause is kotlinx.coroutines.CancellationException) {
                body.cancel(cause)
            }
        }
    }

    actual override suspend fun read(): ByteReadChannel {
        saveJob.await()
        return stateMutex.withLock {
            if (activeReader?.isClosedForRead == false) {
                throw RequestAlreadyConsumedException()
            }

            file.readChannel().also { activeReader = it }
        }
    }

    actual override fun dispose() {
        val failure = CancellationException("The receive cache was disposed", null)
        runCatching {
            saveJob.cancel(failure)
        }
        runCatching {
            activeReader?.cancel(failure)
        }
        runCatching {
            file.delete()
        }
        if (!body.isClosedForRead) {
            runCatching {
                body.cancel(failure)
            }
        }
    }
}
