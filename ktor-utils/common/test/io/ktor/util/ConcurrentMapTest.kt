/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.util.collections.*
import kotlin.test.*

class ConcurrentMapTest {

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
}
