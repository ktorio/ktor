/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.collections

import kotlin.test.*

class ConcurrentSetTest {

    @Test
    fun testSize() {
        val data = ConcurrentSet<Int>()

        assertFails { data.first() }
        assertEquals(0, data.size)

        data.add(1)
        assertEquals(1, data.size)
        assertEquals(1, data.first())

        data.remove(1)
        assertEquals(0, data.size)
        assertFails { data.first() }
    }

    @Test
    fun testAddAfterRemove() {
        val data = ConcurrentSet<Int>()
        data.add(1)
        assertEquals(1, data.size)

        data.remove(1)
        assertEquals(0, data.size)

        data.add(1)
        assertEquals(1, data.size)

        assertEquals(1, data.first())
    }

    @Test
    fun testToString() {
        assertEquals(emptySet<String>().toString(), ConcurrentSet<String>().toString())

        assertEquals(
            "[0, 1, 2, 3, 4]",
            ConcurrentSet<Int>().apply {
                repeat(5) {
                    add(it)
                }
            }.toString()
        )
    }
}
