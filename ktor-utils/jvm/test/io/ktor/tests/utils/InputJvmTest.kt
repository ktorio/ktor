/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.utils

import io.ktor.util.*
import io.ktor.utils.io.streams.*
import kotlin.test.*

class InputJvmTest {

    @Test
    fun testRead() {
        val stream = byteArrayOf(1, 2, 3, 4, 5, 6).inputStream().asInput().asStream()
        assertEquals(1, stream.read())
        assertEquals(2, stream.read())
        assertEquals(3, stream.read())
        stream.skip(2)
        assertEquals(6, stream.read())
        assertEquals(-1, stream.read())
    }

    @Test
    fun testReadBatch() {
        val stream = byteArrayOf(1, 2, 3, 4).inputStream().asInput().asStream()
        stream.skip(1)
        val target = ByteArray(4)
        val read = stream.read(target, 1, 2)
        assertEquals(2, read)
        assertTrue(byteArrayOf(0, 2, 3, 0).contentEquals(target))
    }

    @Test
    fun testReadBatchExcessLength() {
        val stream = byteArrayOf(1, 2, 3).inputStream().asInput().asStream()
        stream.read()
        val target = ByteArray(4)
        val read = stream.read(target, 0, 3)
        assertEquals(2, read)
        assertTrue(byteArrayOf(2, 3, 0, 0).contentEquals(target))
    }

    @Test
    fun testReadBatchOutOfBounds() {
        val stream = byteArrayOf(1, 2, 3).inputStream().asInput().asStream()
        val target = ByteArray(3)
        assertFailsWith(IndexOutOfBoundsException::class) { stream.read(target, 1, 3) }
    }

    @Test
    fun testReadBatchEmpty() {
        val stream = byteArrayOf(1, 2).inputStream().asInput().asStream()
        val skipped = stream.skip(5)
        assertEquals(2, skipped)
        val read = stream.read(byteArrayOf(), 0, 5)
        assertEquals(-1, read)
    }
}
