/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.cio

import io.ktor.http.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.IOException
import kotlinx.io.Sink
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChunkedTest {

    @Test
    fun testEmptyBroken(): Unit = runBlocking {
        val bodyText = ""
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        assertFailsWith<EOFException> {
            decodeChunked(ch, parsed)
        }
    }

    @Test
    fun testChunkedWithContentLength() = runBlocking {
        val chunkedContent = listOf(
            "3\r\n",
            "a=1\r\n",
            "0\r\n",
            "\r\n",
        )

        val input = writer {
            chunkedContent.forEach {
                channel.writeStringUtf8(it)
            }
        }.channel

        val output = ByteChannel()
        launch {
            decodeChunked(input, output)
            output.close()
        }

        val content = output.readRemaining().readText()
        assertEquals("a=1", content)
    }

    @Test
    fun testEmptyWithoutCrLf(): Unit = runBlocking {
        val bodyText = "0"
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        assertFailsWith<EOFException> {
            decodeChunked(ch, parsed)
        }
    }

    @Test
    fun testEmpty() = runBlocking {
        val bodyText = "0\r\n\r\n"
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        decodeChunked(ch, parsed)

        assertEquals(0, parsed.availableForRead)
        assertTrue { parsed.isClosedForRead }
    }

    @Test
    fun testEmptyWithTrailing() = runBlocking {
        val bodyText = "0\r\n\r\ntrailing"
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        decodeChunked(ch, parsed)

        assertEquals(0, parsed.availableForRead)
        assertTrue { parsed.isClosedForRead }
        assertEquals("trailing", ch.readRemaining().readText())
    }

    @Test
    fun testContent() = runBlocking {
        val bodyText = "3\r\n123\r\n0\r\n\r\n"
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        decodeChunked(ch, parsed)

        assertEquals("123", parsed.readUTF8Line())
    }

    @Test
    fun testContentMultipleChunks() = runBlocking {
        val bodyText = "3\r\n123\r\n2\r\n45\r\n1\r\n6\r\n0\r\n\r\n"
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        decodeChunked(ch, parsed)

        assertEquals("123456", parsed.readUTF8Line())
    }

    @Test
    fun testContentMixedLineEndings() = runBlocking {
        val bodyText = "3\n123\n2\r\n45\r\n1\n6\n0\r\n\n"
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        decodeChunked(ch, parsed)

        assertEquals("123456", parsed.readUTF8Line())
    }

    @Test
    fun testContentWithRcLineEnding() = runTest {
        val bodyText = "3\r\n" +
            "123\r1\r\n" + // <- CR line ending after chunk body
            "2\r\n" +
            "45\r\n" +
            "0\r\n\r\n"
        val ch = ByteReadChannel(bodyText.toByteArray())
        val parsed = ByteChannel()

        assertFailsWith<IOException> {
            decodeChunked(ch, parsed)
        }
    }

    @Test
    fun testEncodeEmpty() = runBlocking {
        val encoded = ByteChannel()

        launch(coroutineContext) {
            try {
                encodeChunked(encoded, ByteReadChannel.Empty)
            } finally {
                encoded.close()
            }
        }

        yield()
        val encodedText = encoded.readRemaining().inputStream().reader().readText()
        assertEquals("0\r\n\r\n", encodedText)
    }

    @Test
    fun testEncodeChunks() = runBlocking {
        val output = ByteChannel(false)
        val encoded = ByteChannel()

        launch {
            try {
                encodeChunked(encoded, output)
            } finally {
                encoded.close()
            }
        }

        yield()
        output.writeStringUtf8("123")
        yield()
        output.writeStringUtf8("45")
        yield()
        output.writeStringUtf8("6")
        output.close()
        yield()

        val encodedText = encoded.readRemaining().inputStream().reader().readText()
        assertEquals("6\r\n123456\r\n0\r\n\r\n", encodedText)
    }

    @Test
    fun longLoop() = runBlocking {
        val content = ByteChannel(true)
        val encoded = ByteChannel()
        val decoded = ByteChannel()

        val written = CompletableDeferred<String>()
        val read = CompletableDeferred<String>()

        launch(coroutineContext) {
            val sb = StringBuilder()
            repeat(7000) {
                val s = "Ab7CdEfZ".take(it + 1)
                sb.append(s)
                content.writeStringUtf8(s)
            }
            content.close()
            written.complete(sb.toString())
        }

        launch(coroutineContext) {
            try {
                encodeChunked(encoded, content)
            } finally {
                encoded.close()
            }
        }

        launch(coroutineContext) {
            decodeChunked(encoded, decoded)
        }

        launch(coroutineContext) {
            try {
                val sb = StringBuilder()
                val bb = ByteBuffer.allocate(256)

                while (true) {
                    bb.clear()
                    val rc = decoded.readAvailable(bb)
                    if (rc == -1) break
                    bb.flip()
                    val text = String(bb.array(), 0, bb.remaining())

                    sb.append(text)
                }

                read.complete(sb.toString())
            } catch (t: Throwable) {
                read.completeExceptionally(t)
            }
        }

        val first = written.await()
        val second = read.await()

        assertEquals(first, second)
    }

    @Test
    fun exceptionDuringWrite() = runTest {
        val defunctWriteChannel = object : ByteWriteChannel {
            override val isClosedForWrite: Boolean get() = false
            override val closedCause: Throwable? get() = null

            @InternalAPI
            override val writeBuffer: Sink = Buffer()
            override suspend fun flush() {
                throw IOException()
            }

            override suspend fun flushAndClose() {
                throw IOException()
            }

            override fun cancel(cause: Throwable?) {}
        }

        assertFailsWith<IOException> {
            encodeChunked(defunctWriteChannel, ByteReadChannel("123"))
        }
    }
}
