/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.test.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

class CountedByteWriteChannelTest {

    @Test
    fun flushOfPlainChannelSucceedsWhenReaderDrainsAndCancelsBeforeWriterResumes() = runTest {
        val channel = ByteChannel()
        drainAndCancelWhileFlushing(channel, channel)
    }

    @Test
    fun flushOfCountedChannelSucceedsWhenReaderDrainsAndCancelsBeforeWriterResumes() = runTest {
        val channel = ByteChannel()
        drainAndCancelWhileFlushing(channel, CountedByteWriteChannel(channel))
    }

    /**
     * Writes a full channel's worth of bytes through [writer], which makes it flush and suspend until [channel]'s
     * reader catches up. The reader then takes every byte and cancels [channel] before the writer coroutine gets to run
     * again, the way a parser that closes its input stream after reading a whole document does.
     */
    private suspend fun CoroutineScope.drainAndCancelWhileFlushing(channel: ByteChannel, writer: ByteWriteChannel) {
        val write = async {
            writer.writeFully(ByteArray(CHANNEL_MAX_SIZE))
        }

        assertEquals(CHANNEL_MAX_SIZE, channel.readByteArray(CHANNEL_MAX_SIZE).size)
        channel.cancel()

        write.await()
    }
}
