/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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

    private fun create(): ConcurrentMap<Int, Int> = ConcurrentMap<Int, Int>()
}
