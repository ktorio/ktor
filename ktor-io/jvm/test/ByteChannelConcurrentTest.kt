/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class ByteChannelConcurrentTest {

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun cannotSubscribeTwiceForContent() = runTest {
        val channel = ByteChannel()
        val read1 = GlobalScope.async { channel.readByte() }
        val read2 = GlobalScope.async { channel.readByte() }

        assertFailsWith<IllegalStateException> {
            awaitAll(read1, read2)
        }.also {
            assertEquals("Concurrent read attempts", it.message)
        }
    }

    @OptIn(DelicateCoroutinesApi::class, InternalAPI::class)
    @Test
    fun cannotSubscribeTwiceForFlush() = runTest {
        val channel = ByteChannel()
        val exceptionHandler = CoroutineExceptionHandler { _, cause ->
            assertTrue(cause is IllegalStateException)
            assertEquals("Concurrent write attempts", cause.message)
        }
        val write1 = GlobalScope.launch(exceptionHandler) {
            channel.writeByteArray(ByteArray(CHANNEL_MAX_SIZE))
        }
        val write2 = GlobalScope.launch(exceptionHandler) {
            // concurrent writes can also cause NPEs
            delay(100)
            channel.writeByteArray(ByteArray(CHANNEL_MAX_SIZE))
        }
        val read = GlobalScope.async {
            delay(100)
            channel.readBuffer(CHANNEL_MAX_SIZE * 2)
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
}
