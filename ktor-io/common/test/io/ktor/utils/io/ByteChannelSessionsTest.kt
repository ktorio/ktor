/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class ByteChannelSessionsTest : ByteChannelTestBase() {
    @Test
    fun testSessionReadingSingleChunk(): Unit = runTest {
        coroutines.schedule {
            expect(2)
            ch.writeStringUtf8("ABC")
            ch.close()
        }

        expect(1)

        val first = ch.read { _, start, endExclusive ->
            assertEquals(3, endExclusive - start, "yo")
            0
        }

        assertEquals(0, first)

        expect(3)

        assertEquals(
            1,
            ch.read { buffer, start, endExclusive ->
                assertEquals(3, endExclusive - start)
                assertEquals('A'.toByte(), buffer[start])
                1
            }
        )

        assertEquals(
            2,
            ch.read { buffer, start, endExclusive ->
                assertEquals(2, endExclusive - start)
                assertEquals('B'.toByte(), buffer[start])
                assertEquals('C'.toByte(), buffer[start + 1])
                2
            }
        )

        assertTrue(ch.isClosedForRead, "Should be closed after all bytes read.")

        assertEquals(
            0,
            ch.read { _, start, endExclusive ->
                assertEquals(0, endExclusive - start)
                0
            }
        )

        finish(4)
    }

    @Test
    fun testSessionReadingMultipleChunks(): Unit = runTest {
        val text = buildString {
            repeat(16384) { index ->
                append("0123456789ABCDEF".let { it[index % it.length] })
            }
        }
        coroutines.schedule {
            ch.writeStringUtf8(text)
            ch.close()
        }

        expect(1)

        var bytesRead = 0
        var completed = false

        while (!completed) {
            var size: Int = -1

            val result = ch.read { source, start, endExclusive ->
                check(start >= 0)
                check(endExclusive >= start)
                check(source.size32 >= (endExclusive - start))
                check(endExclusive <= source.size)

                val endIndex = endExclusive.minus(7)
                    .coerceAtLeast(start + 10)
                    .coerceAtMost(endExclusive)

                var textOffset = bytesRead
                for (index in start until endIndex) {
                    assertEquals(
                        text[textOffset].toByte(),
                        source[index],
                        "Expected character '${text[textOffset]}', " +
                            "got '${source[index].toChar()}', index $bytesRead + $index"
                    )
                    textOffset++
                }

                size = (endIndex - start).toInt()
                if (size < 0) {
                    error(
                        "Size is negative: $size, start = $start, endIndex = $endIndex, " +
                            "endExclusive: $endExclusive"
                    )
                }
                bytesRead += size

                if (size == 0 && bytesRead == text.length) {
                    completed = true
                }

                size
            }

            assertEquals(size, result)
        }

        assertTrue(ch.isClosedForRead, "Should be closed after all bytes read.")
    }

    @Test
    fun testSessionReadingSessionMultipleChunks(): Unit = runTest {
        val text = buildString {
            repeat(16384) { index ->
                append("0123456789ABCDEF".let { it[index % it.length] })
            }
        }
        coroutines.schedule {
            ch.writeStringUtf8(text)
            ch.close()
        }

        expect(1)

        var bytesRead = 0

        @Suppress("DEPRECATION")
        ch.readSuspendableSession {
            while (true) {
                val buffer = request(1)
                if (buffer != null) {
                    var count = 0
                    (buffer as Buffer).forEach { byte ->
                        assertEquals(text[bytesRead].toByte(), byte, "Broken character at index $bytesRead")
                        count++
                        bytesRead++
                    }
                    discard(count)
                } else if (bytesRead < text.length) {
                    await(1)
                } else {
                    break
                }
            }
        }

        yield()
        assertTrue(ch.isClosedForRead, "Should be closed after all bytes read.")
    }
}
