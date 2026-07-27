/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.nio

import io.ktor.test.*
import io.ktor.utils.io.*
import java.io.ByteArrayInputStream
import java.nio.channels.Channels
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReadingTest {

    @Test
    fun readsFromByteChannel() = runTest {
        val expected = "This is a test string"
        val channel = Channels.newChannel(ByteArrayInputStream(expected.encodeToByteArray()))
        val actual = channel.toByteReadChannel().readBuffer().readText()
        assertEquals(expected, actual)
    }

    @Test
    fun readsFromFileChannelAndCloses() = runTest {
        val expected = "This is a test string"
        val temp = Files.createTempFile("file", "txt")
        temp.writeText(expected)
        val channel = Files.newByteChannel(temp)
        val actual = channel.toByteReadChannel().readBuffer().readText()
        assertEquals(expected, actual)
        assertFalse(channel.isOpen)
    }
}
