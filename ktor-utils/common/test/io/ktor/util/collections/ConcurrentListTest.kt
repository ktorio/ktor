/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections

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

        assertNotEquals(
            listOf(1),
            ConcurrentList<Int>().apply {
                add(2)
            }
        )

        assertNotEquals(
            listOf(1),
            ConcurrentList<Int>().apply {
                add(1)
                add(2)
            }
        )

        assertEquals(
            listOf(1, 2),
            ConcurrentList<Int>().apply {
                add(1)
                add(2)
            }
        )

        assertNotEquals(
            listOf(1, 2, 3),
            ConcurrentList<Int>().apply {
                add(1)
                add(2)
            }
        )
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

    @Test
    fun testAddWithIndex() {
        val list = ConcurrentList<Int>().apply {
            add(1)
            add(1, 50)
            add(1, 42)
            add(1, 39)
        }

        val expected = mutableListOf<Int>().apply {
            add(1)
            add(1, 50)
            add(1, 42)
            add(1, 39)
        }

        assertEquals(expected, list)
    }

    @Test
    fun testListIteratorRemove() {
        val list = ConcurrentList<Int>().apply {
            repeat(10) {
                add(it)
            }
        }

        list.removeAll { it == 0 }
        assertEquals("[1, 2, 3, 4, 5, 6, 7, 8, 9]", list.toString())
    }

    @Test
    fun testListIteratorRemovePreLast() {
        val list = ConcurrentList<Int>()
        list.addAll(listOf(1, 2))

        val iterator = list.iterator()
        assertEquals(1, iterator.next())

        iterator.remove()

        assertEquals(2, iterator.next())
    }

    @Test
    fun testListIteratorAdd() {
        val list = ConcurrentList<Int>()
        val iterator = list.listIterator()

        repeat(100) {
            iterator.add(it)
            assertEquals(it, iterator.next())
        }

        assertEquals(List(100) { it }, list)
    }
}
