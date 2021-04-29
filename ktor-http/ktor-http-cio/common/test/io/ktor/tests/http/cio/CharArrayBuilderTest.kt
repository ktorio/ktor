/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.http.cio

import io.ktor.http.cio.internals.*
import io.ktor.utils.io.pool.*
import kotlin.test.*

class CharArrayBuilderTest {
    private val pool = Pool()
    private val builder = CharArrayBuilder(pool)

    @AfterTest
    fun tearDown() {
        builder.release()
        assertEquals(0, pool.size)
    }

    @Test
    fun testAppendSingleChar() {
        assertEquals(0, builder.length)
        assertFails {
            builder[0]
        }
        assertFails {
            builder[1]
        }
        assertFails {
            builder[CHAR_BUFFER_ARRAY_LENGTH]
        }
        assertFails {
            builder[CHAR_BUFFER_ARRAY_LENGTH + 1]
        }

        builder.append('1')
        assertEquals('1', builder[0])
        assertEquals("1", builder.toString())

        assertFails {
            builder[1]
        }
        assertFails {
            builder[CHAR_BUFFER_ARRAY_LENGTH]
        }
        assertFails {
            builder[CHAR_BUFFER_ARRAY_LENGTH + 1]
        }
    }

    @Test
    fun testAppendFewChars() {
        assertEquals(0, builder.length)
        assertFails {
            builder[0]
        }
        assertFails {
            builder[1]
        }
        assertFails {
            builder[CHAR_BUFFER_ARRAY_LENGTH]
        }
        assertFails {
            builder[CHAR_BUFFER_ARRAY_LENGTH + 1]
        }

        for (i in 1..10) {
            builder.append('0' + i)
        }

        assertEquals('1', builder[0])
        assertEquals('2', builder[1])
        assertEquals('3', builder[2])
        assertEquals('4', builder[3])
        assertEquals('5', builder[4])
        assertEquals('6', builder[5])
        assertEquals('7', builder[6])
        assertEquals('8', builder[7])
        assertEquals('9', builder[8])
        assertEquals(':', builder[9])

        assertEquals("123456789:", builder.toString())

        assertFails {
            builder[CHAR_BUFFER_ARRAY_LENGTH]
        }
        assertFails {
            builder[CHAR_BUFFER_ARRAY_LENGTH + 1]
        }
    }

    @Test
    fun testAppendManyChars() {
        val text = buildText()

        for (i in text.indices) {
            builder.append(text[i])
        }

        assertEquals(text.length, builder.length)
        assertEquals(text, builder.toString())
    }

    @Test
    fun testAppendSubStrings() {
        val text = buildText()

        var i = 0
        var length = 1

        while (i < text.length) {
            builder.append(text.substring(i, minOf(text.length, i + length)))
            i += length
            length += maxOf(1, length / 2)
        }

        assertEquals(text.length, builder.length)
        assertEquals(text, builder.toString())
    }

    @Test
    fun testSubString() {
        val text = buildText()
        builder.append(text)

        var i = 0
        var length = 1

        while (i < text.length) {
            val end = minOf(text.length, i + length)
            assertEquals(text.substring(i, end), builder.substring(i, end), "substring for range [$i, $end)")
            i += length
            length += maxOf(1, length / 2)
        }
    }

    private fun buildText(): String {
        return buildString {
            for (i in 1..65536) {
                append('0' + (i and 0xf))
            }
        }
    }

    private class Pool : NoPoolImpl<CharArray>() {
        private val unreleased = mutableListOf<CharArray>()

        val size: Int get() = unreleased.size

        override fun borrow(): CharArray {
            val buffer = CharArray(CHAR_BUFFER_ARRAY_LENGTH)
            unreleased.add(buffer)
            return buffer
        }

        override fun recycle(instance: CharArray) {
            if (!unreleased.remove(instance)) {
                throw IllegalStateException("Recycling a buffer that hasn't been borrowed from this pool")
            }

            super.recycle(instance)
        }

        override fun dispose() {
            throw UnsupportedOperationException()
        }
    }
}
