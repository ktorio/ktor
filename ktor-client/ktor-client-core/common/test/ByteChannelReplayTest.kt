/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import io.ktor.client.plugins.internal.*
import io.ktor.utils.io.*
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ByteChannelReplayTest {

    private val size = 1024 * 1024 + 1
    private val expectedByte = 'A'.code.toByte()
    private val expected = ByteArray(size).apply { fill(expectedByte) }
    private lateinit var channelReplay: ByteChannelReplay

    @BeforeTest
    fun setup() {
        channelReplay = ByteChannelReplay(ByteReadChannel(expected))
    }

    @Test
    fun readFirst() = runTest {
        val first = channelReplay.replay()
        assertRead(first)
        val second = channelReplay.replay()
        assertRead(second)
    }

    @Test
    fun readSecond() = runTest {
        val first = channelReplay.replay()
        val second = channelReplay.replay()
        assertRead(second)
        assertTrue(first.isClosedForRead)
    }

    @Test
    fun readABunch() = runTest {
        val jobs = (0..5).map {
            launch {
                val readChannel = channelReplay.replay()
                yield()
                try {
                    assertRead(readChannel)
                } catch (e: Exception) {
                    assertEquals("Save body abandoned", e.message)
                }
            }
        }
        joinAll(*jobs.toTypedArray())
    }

    private suspend fun assertRead(readChannel: ByteReadChannel) {
        repeat(size) { i ->
            assertEquals(expectedByte, readChannel.readByte(), "Incorrect byte at index $i")
        }
        assertTrue(readChannel.isClosedForRead || readChannel.exhausted())
    }
}
