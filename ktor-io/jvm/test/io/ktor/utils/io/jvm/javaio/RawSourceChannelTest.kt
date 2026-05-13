/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.javaio

import io.ktor.utils.io.*
import kotlinx.coroutines.test.*
import kotlinx.io.*
import kotlin.test.*

class RawSourceChannelTest {

    @Test
    fun `awaitContent returns false when channel is closed with empty buffer`() = runTest {
        val channel = ByteArray(0).inputStream().toByteReadChannel()

        // First call closes the channel internally (EOF)
        val firstResult = channel.awaitContent(1)
        assertFalse(firstResult, "awaitContent should return false on EOF")

        // Second call hits the closedToken != null path
        val secondResult = channel.awaitContent(1)
        assertFalse(secondResult, "awaitContent should return false when channel is already closed with empty buffer")
    }

    @Test
    fun `awaitContent returns true when channel is closed with data in buffer`() = runTest {
        val channel = ByteArray(10) { it.toByte() }.inputStream().toByteReadChannel()

        // Request more than available to trigger EOF and close the channel
        val firstResult = channel.awaitContent(20)
        assertFalse(firstResult, "awaitContent should return false when not enough data")

        // Channel is now closed, but 10 bytes remain in the buffer
        val secondResult = channel.awaitContent(1)
        assertTrue(secondResult, "awaitContent should return true when closed channel still has data in buffer")
    }

    @Test
    fun `awaitContent throws when channel is cancelled`() = runTest {
        val channel = ByteArray(0).inputStream().toByteReadChannel()
        channel.cancel(IOException("test cancellation"))
        assertFailsWith<IOException> {
            channel.awaitContent(1)
        }
    }
}
