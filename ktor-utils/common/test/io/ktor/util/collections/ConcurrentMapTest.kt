/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections

import kotlin.test.*

class ConcurrentMapTest {

    @Test
    fun testSize() {
        val map = create()
        assertEquals(0, map.size)

        map[1] = 1
        assertEquals(1, map.size)

        map[1] = 2
        assertEquals(1, map.size)

        map[2] = 2
        assertEquals(2, map.size)

        map.remove(1)
        assertEquals(1, map.size)
    }

    @Test
    fun testContainsKey() {
        val map = create()

        assertFalse { 1 in map }
        map[1] = 1

        assertTrue { 1 in map }

        map.remove(1)
        assertFalse { 1 in map }
    }

    @Test
    fun testForEach() {
        val map = create()

        map[1] = 2

        var count = 0
        map.forEach { (key, value) ->
            assertEquals(1, key)
            assertEquals(2, value)

            count += 1
        }

        assertEquals(1, count)
    }

    @Test
    fun testToString() {
        assertEquals(emptyMap<Int, Int>().toString(), create().toString())

        assertEquals(
            mutableMapOf(1 to 2, 3 to 4, 5 to 6).toString(),
            ConcurrentMap<Int, Int>().apply {
                this[1] = 2
                this[3] = 4
                this[5] = 6
            }.toString()
        )
    }

    @Test
    fun testEmpty() {
        val map = ConcurrentMap<Int, Int>()
        assertEquals(0, map.size)
        assertTrue(map.isEmpty())
    }

    @Test
    fun testAdd() {
        val map = ConcurrentMap<Int, Int>()
        map[0] = 1
        assertTrue(map.containsKey(0))
        assertTrue(map.containsValue(1))

        assertEquals(1, map[0])

        var size = 0
        map.forEach { size++ }
        assertEquals(1, size)
    }

    @Test
    fun testRemove() {
        val map = ConcurrentMap<Int, Int>()
        map[0] = 1

        map.remove(0)
        assertFalse(map.containsKey(0))
        assertFalse(map.containsValue(1))

        var size = 0
        map.forEach { size++ }
        assertEquals(0, size)
    }

    @Test
    fun testMany() {
        val map = ConcurrentMap<Int, Int>()

        repeat(10000) {
            map[it] = it + 1
        }

        repeat(10000) {
            assertEquals(it + 1, map[it])
        }

        repeat(10000) {
            map[it] = it - 1
        }

        repeat(10000) {
            assertEquals(it - 1, map[it])
        }
    }

    @Test
    fun testRemoveWithOverlappingHashCodes() {
        val map = ConcurrentMap<A, Any>()
        map[A("x")] = "val"
        map[A("y")] = true
        assertEquals(true, map[A("y")])
        map.remove(A("x"))
        assertNull(map[A("x")])
        assertEquals(true, map[A("y")])
    }

    class A(val x: String) {
        override fun equals(other: Any?): Boolean = (other as A).x == x
        override fun hashCode(): Int = 1
        override fun toString(): String = "A($x)"
    }

    private fun create(): ConcurrentMap<Int, Int> = ConcurrentMap()
}
