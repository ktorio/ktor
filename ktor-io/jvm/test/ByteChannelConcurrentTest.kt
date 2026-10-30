/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.test.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

class ByteChannelConcurrentTest {

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun cannotSubscribeTwiceForContent() = runTest {
        val channel = ByteChannel()
        val read1 = GlobalScope.async { channel.readByte() }
        val read2 = GlobalScope.async { channel.readByte() }

        assertFailsWith<ConcurrentIOException> {
            awaitAll(read1, read2)
        }.also {
            assertEquals("Concurrent read attempts", it.message)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun cannotSubscribeTwiceForFlush() = runTest {
        val channel = ByteChannel()
        var contentReady = false
        val exceptionHandler = CoroutineExceptionHandler { _, cause ->
            assertTrue(cause is ConcurrentIOException)
            assertEquals("Concurrent write attempts", cause.message)
        }
        val write1 = GlobalScope.launch(exceptionHandler) {
            channel.writeByteArray(ByteArray(CHANNEL_MAX_SIZE))
        }
        val write2 = GlobalScope.launch(exceptionHandler) {
            channel.awaitContent()
            contentReady = true
            channel.writeByteArray(ByteArray(CHANNEL_MAX_SIZE))
        }
        val read = GlobalScope.async {
            // ensure the first write has finished before reading
            while (!contentReady) {
                delay(100)
            }
            channel.readBuffer(CHANNEL_MAX_SIZE * 2L)
            channel.close()
        }

        joinAll(write1, write2, read)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testReadAndWriteConcurrentWithCopyTo() = runBlocking {
        repeat(10000) {
            val result = ByteChannel()

            val writer = GlobalScope.reader {
                channel.copyAndClose(result)
            }.channel

            val content = byteArrayOf(1, 2)
            writer.writeByteArray(content)
            writer.flushAndClose()

            assertContentEquals(content, result.readByteArray(2))
        }
    }

    /**
     * KTOR-7729: a third coroutine only *queries* `availableForRead` from another thread.
     *
     * Before the fix, `availableForRead` went through the `readBuffer` getter, which calls `moveFlushToReadBuffer()`
     * and appends to the channel's internal read buffer while the reader is consuming it on another thread.
     */
    @Test
    fun testConcurrentAvailableForRead() = runBlocking {
        repeat(200) {
            val channel = ByteChannel()
            raceReadSide(channel) {
                while (isActive && !channel.isClosedForRead) {
                    channel.availableForRead
                }
            }
        }
    }

    /**
     * Runs a writer, a verifying reader and [intruder] on different threads against the same [channel].
     *
     * Corrupted buffers show up as exceptions from `Segment.compact` / `Buffer.write` (like the
     * `Check failed.` in the report), lost bytes (`EOFException`), wrong bytes, or a reader that never completes.
     */
    private suspend fun raceReadSide(channel: ByteChannel, intruder: suspend CoroutineScope.() -> Unit) {
        val size = 256 * 1024
        withTimeout(10_000) {
            coroutineScope {
                launch(Dispatchers.Default) {
                    var written = 0
                    var chunk = 1
                    while (written < size) {
                        val count = minOf(chunk, size - written)
                        channel.writeByteArray(ByteArray(count) { patternByte(written + it) })
                        channel.flush()
                        written += count
                        chunk = chunk % 1000 + 7 // varying sizes -> partially filled segments
                    }
                    channel.flushAndClose()
                }

                launch(Dispatchers.Default, block = intruder)

                launch(Dispatchers.Default) {
                    for (index in 0 until size) {
                        val byte = channel.readByte()
                        if (byte != patternByte(index)) {
                            fail("Wrong byte at $index: expected ${patternByte(index)}, actual $byte")
                        }
                    }
                }
            }
        }
    }

    private fun patternByte(index: Int): Byte = (index % 251).toByte()
}
