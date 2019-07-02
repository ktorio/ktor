/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.coroutines.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

private const val CHUNK_BUFFER_SIZE = 4096L

/**
 * Split source [ByteReadChannel] into 2 new one.
 * Cancel of one channel in split(input or both outputs) cancels other channels.
 */
@KtorExperimentalAPI
fun ByteReadChannel.split(coroutineScope: CoroutineScope): Pair<ByteReadChannel, ByteReadChannel> {
    val first = ByteChannel(autoFlush = true)
    val second = ByteChannel(autoFlush = true)

    coroutineScope.launch {
        try {
            while (!this@split.isClosedForRead) {
                this@split.readRemaining(CHUNK_BUFFER_SIZE).use { chunk ->
                    listOf(
                        async { first.writePacket(chunk.copy()) },
                        async { second.writePacket(chunk.copy()) }
                    ).awaitAll()
                }
            }
        } catch (cause: Throwable) {
            this@split.cancel(cause)
            first.cancel(cause)
            second.cancel(cause)
        } finally {
            first.close()
            second.close()
        }
    }

    return first to second
}

/**
 * Read channel to byte array.
 */
@KtorExperimentalAPI
suspend fun ByteReadChannel.toByteArray(): ByteArray = readRemaining().readBytes()
