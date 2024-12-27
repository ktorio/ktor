/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

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
    private val lock = Mutex(locked = true)
    private val file = File.createTempFile("ktor-double-receive-cache", ".tmp")

    @OptIn(DelicateCoroutinesApi::class)
    private val saveJob = GlobalScope.launch(context + Dispatchers.IO) {
        val buffer = ByteBuffer.allocate(bufferSize)

        try {
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
        } finally {
            lock.unlock()
        }
    }

    actual override suspend fun read(): ByteReadChannel =
        lock.withLock {
            file.readChannel()
        }

    actual override fun dispose() {
        runCatching {
            saveJob.cancel()
        }
        runCatching {
            file.delete()
        }
        if (!body.isClosedForRead) {
            runCatching {
                body.cancel()
            }
        }
    }
}
