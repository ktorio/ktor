/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.bits.*
import kotlinx.cinterop.*
import kotlin.test.*

class MemoryTest {

    private lateinit var mem: Memory

    @BeforeTest
    fun setup() {
        mem = DefaultAllocator.alloc(1024)
    }

    @AfterTest
    fun cleanup() {
        DefaultAllocator.free(mem)
    }

    @Test
    fun testShortAlign() {
        assertTrue(mem.isAlignedShort(0))
        assertFalse(mem.isAlignedShort(1))

        assertTrue(mem.isAlignedShort(510))
        assertFalse(mem.isAlignedShort(511))
        assertTrue(mem.isAlignedShort(512))
    }

    @Test
    fun testIntAlign() {
        assertTrue(mem.isAlignedInt(0))
        assertFalse(mem.isAlignedInt(1))
        assertFalse(mem.isAlignedInt(2))
        assertFalse(mem.isAlignedInt(3))

        assertTrue(mem.isAlignedInt(508))
        assertFalse(mem.isAlignedInt(509))
        assertFalse(mem.isAlignedInt(510))
        assertFalse(mem.isAlignedInt(511))
        assertTrue(mem.isAlignedInt(512))
    }

    @Test
    fun testLongAlign() {
        assertTrue(mem.isAlignedLong(0))
        assertFalse(mem.isAlignedLong(1))
        assertFalse(mem.isAlignedLong(2))
        assertFalse(mem.isAlignedLong(3))
        assertFalse(mem.isAlignedLong(4))
        assertFalse(mem.isAlignedLong(6))
        assertFalse(mem.isAlignedLong(6))
        assertFalse(mem.isAlignedLong(7))

        assertTrue(mem.isAlignedLong(504))
        assertFalse(mem.isAlignedLong(505))
        assertFalse(mem.isAlignedLong(506))
        assertFalse(mem.isAlignedLong(507))
        assertFalse(mem.isAlignedLong(508))
        assertFalse(mem.isAlignedLong(509))
        assertFalse(mem.isAlignedLong(510))
        assertFalse(mem.isAlignedLong(511))
        assertTrue(mem.isAlignedLong(512))
    }
}
