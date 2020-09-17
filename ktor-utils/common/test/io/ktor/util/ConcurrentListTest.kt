/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.util.collections.*
import kotlin.random.*
import kotlin.test.*

class ConcurrentListTest {

    @Test
    fun testEmpty() {
        val list = ConcurrentList<Unit>()

        assertEquals(0, list.size)
        assertTrue(list.isEmpty())

        list.add(Unit)
        list.remove(Unit)

        assertEquals(0, list.size)
        assertTrue(list.isEmpty())
    }

    @Test
    fun testEquals() {
        assertEquals(emptyList<Unit>(), ConcurrentList())

        val single = ConcurrentList<Int>().apply {
            add(1)
        }

        assertEquals(listOf(1), single)

        assertNotEquals(listOf(1), ConcurrentList<Int>().apply {
            add(2)
        })

        assertNotEquals(listOf(1), ConcurrentList<Int>().apply {
            add(1)
            add(2)
        })

        assertEquals(listOf(1, 2), ConcurrentList<Int>().apply {
            add(1)
            add(2)
        })

        assertNotEquals(listOf(1, 2, 3), ConcurrentList<Int>().apply {
            add(1)
            add(2)
        })
    }

    @Test
    fun testIncreaseCapacity() {
        val list = ConcurrentList<Int>()

        repeat(1024) {
            list.add(it)
        }

        assertEquals(1024, list.size)

        repeat(1024) {
            assertEquals(it, list[it])
        }
    }

    @Test
    fun testClearHasNoExceptions() {
        ConcurrentList<Unit>().clear()
    }
}
