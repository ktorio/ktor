package org.jetbrains.ktor.tests.transform

import org.jetbrains.ktor.transform.*
import org.junit.*
import java.nio.*
import kotlin.test.*

class TransformTableTest {
    val table = TransformTable()

    @Test
    fun testStringToIntSingle() {
        table.register<String>(handler = { it.length })

        assertEquals(2, table.transform("ok"))
        assertEquals(1, table.transform("a"))
    }

    @Test
    fun testStringToIntMultiple() {
        table.register<String>(handler = { it.length })
        table.register<String>(handler = { it.length })

        assertEquals(2, table.transform("ok"))
        assertEquals(1, table.transform("a"))
    }

    @Test
    fun testHierarchy() {
        table.register<String>(handler = { it.length })
        table.register<CharSequence>(handler = { -1 })

        assertEquals(2, table.transform("ok"))
        assertEquals(-1, table.transform(StringBuilder()))
    }

    @Test
    fun testHierarchyX3() {
        table.register<String>(handler = { if (it == "x3") it else it.length })
        table.register<CharSequence>(handler = { -1 })

        assertEquals(2, table.transform("ok"))
        assertEquals(-1, table.transform(StringBuilder()))
        assertEquals(-1, table.transform("x3"))
    }

    @Test
    fun testHierarchyX3Order() {
        val order = mutableListOf<String>()
        table.register<String>(handler = { order.add("first"); it })
        table.register<CharSequence>(handler = { order.add("second"); it })

        assertEquals("ok", table.transform("ok"))

        assertEquals(listOf("first", "second"), order)
    }

    @Test
    fun testNothingRegistered() {
        assertEquals("ok", table.transform("ok"))
        assertEquals(1, table.transform(1))
    }

    @Test
    fun testUnknownType() {
        table.register<CharSequence>(handler = { -1 })

        assertEquals(1, table.transform(1))
    }

    @Test
    fun testNobodyCanHandle() {
        table.register<String>(handler = { it })
        table.register<String>(handler = { it })
        table.register<CharSequence>(handler = { it })

        assertEquals("ok", table.transform("ok"))
    }

    @Test
    fun testCycle() {
        table.register<CharSequence>(handler = { CharBuffer.wrap(it) })

        val obj = "ok"
        val result = table.transform(obj)

        assertFalse { obj === result }
        assertEquals(obj, result.toString())
    }

    @Test
    fun testCycle2() {
        table.register<Int>(handler = { it + 1 })

        assertEquals(2, table.transform(1))
    }

    @Test
    fun testMultipleHandlersChain() {
        table.register<Int>(handler = { it + 1 })
        table.register<Int>(handler = { it + 1 })
        table.register<Int>(handler = { it + 1 })

        assertEquals(3, table.transform(0))
    }

    @Test
    fun testPredicate() {
        table.register<Int>(predicate = { it >= 0 }, handler = { it + 1 })

        assertEquals(1, table.transform(0))
        assertEquals(2, table.transform(1))
        assertEquals(-1, table.transform(-1))
    }

    @Test
    fun testPredicateCycle() {
        table.register<Int>(predicate = { it > 0 }, handler = { it + 1 })
        table.register<Int>(handler = { it + 1 })

        assertEquals(2, table.transform(0))
        assertEquals(0, table.transform(-1))
    }
}