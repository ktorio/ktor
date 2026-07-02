/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

import kotlin.test.*

class ConcurrentSetTest {

    @Test
    fun `add remove and clear`() {
        val set = ConcurrentSet<String>()

        assertTrue(set.add("a"))
        assertFalse(set.add("a"))
        assertEquals(1, set.size)
        assertTrue(set.contains("a"))

        assertTrue(set.remove("a"))
        assertFalse(set.remove("a"))
        assertTrue(set.isEmpty())

        assertTrue(set.addAll(listOf("x", "y")))
        set.clear()
        assertTrue(set.isEmpty())
    }

    @Test
    fun `addAll returns true when set was modified`() {
        val set = ConcurrentSet<String>()
        assertTrue(set.addAll(listOf("existing")))
        assertTrue(set.addAll(listOf("existing", "new")))
        assertEquals(setOf("existing", "new"), set.toSet())
    }

    @Test
    fun `addAll returns false when nothing was added`() {
        val set = ConcurrentSet<String>()
        assertTrue(set.addAll(listOf("a", "b")))
        assertFalse(set.addAll(listOf("a", "b")))
        assertFalse(set.addAll(emptyList()))
    }

    @Test
    fun `removeAll collection returns true when set was modified`() {
        val set = ConcurrentSet<String>()
        set.addAll(listOf("a", "b", "c"))

        assertTrue(set.removeAll(listOf("missing", "b")))
        assertEquals(setOf("a", "c"), set.toSet())
    }

    @Test
    fun `removeAll collection returns false when nothing was removed`() {
        val set = ConcurrentSet<String>()
        set.addAll(listOf("a", "b"))

        assertFalse(set.removeAll(listOf("missing")))
    }

    @Test
    fun `removeAll with predicate removes matching elements`() {
        val set = ConcurrentSet<String>()
        set.addAll(listOf("keep-1", "remove-1", "keep-2", "remove-2"))

        assertTrue(set.removeAll { it.startsWith("remove") })
        assertEquals(setOf("keep-1", "keep-2"), set.toSet())
    }

    @Test
    fun `iterator remove removes from delegate not snapshot`() {
        val set = ConcurrentSet<String>()
        set.addAll(listOf("a", "b", "c"))

        val iterator = set.iterator()
        while (iterator.hasNext()) {
            if (iterator.next() == "b") {
                iterator.remove()
            }
        }

        assertEquals(setOf("a", "c"), set.toSet())
        assertEquals(2, set.size)
    }

    @Test
    fun `retainAll keeps only matching elements`() {
        val set = ConcurrentSet<String>()
        set.addAll(listOf("a", "b", "c", "d"))

        assertTrue(set.retainAll(setOf("b", "d", "missing")))
        assertEquals(setOf("b", "d"), set.toSet())
    }

    @Test
    fun `containsAll checks set contains requested elements`() {
        val set = ConcurrentSet<String>()
        set.addAll(listOf("a", "b", "c"))

        assertTrue(set.containsAll(listOf("a", "c")))
        assertFalse(set.containsAll(listOf("a", "missing")))
    }

    @Test
    fun `remove and readd same instance`() {
        val set = ConcurrentSet<String>()
        val value = "entry"

        assertTrue(set.add(value))
        assertFalse(set.add(value))

        set.remove(value)
        assertTrue(set.add(value))

        assertEquals(1, set.size)
        assertTrue(set.contains(value))
    }
}
