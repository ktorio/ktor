/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.nio

import io.ktor.utils.io.*
import kotlinx.coroutines.test.*
import java.io.*
import java.nio.channels.*
import java.nio.file.*
import kotlin.io.path.*
import kotlin.test.*

class ReadingTest {

    @Test
    fun readsFromByteChannel() = runTest {
        val expected = "This is a test string"
        val channel = Channels.newChannel(ByteArrayInputStream(expected.encodeToByteArray()))
        val actual = channel.toByteReadChannel().readRemaining().readText()
        assertEquals(expected, actual)
    }

    @Test
    fun readsFromFileChannelAndCloses() = runTest {
        val expected = "This is a test string"
        val temp = Files.createTempFile("file", "txt")
        temp.writeText(expected)
        val channel = Files.newByteChannel(temp)
        val actual = channel.toByteReadChannel().readRemaining().readText()
        assertEquals(expected, actual)
        assertFalse(channel.isOpen)
    }
}
