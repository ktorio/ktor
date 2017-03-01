package org.jetbrains.ktor.tests.transform

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.transform.*
import org.junit.*
import java.nio.*
import java.util.*
import kotlin.test.*

class TransformTableTest {
    @Test
    fun testStringToIntSingle() = runBlocking {
        val table = TransformTable<Unit>()
        table.register<String>(handler = { it.length })

        assertEquals(2, table.transform(Unit, "ok"))
        assertEquals(1, table.transform(Unit, "a"))
    }

    @Test
    fun testStringToIntMultiple() = runBlocking {
        val table = TransformTable<Unit>()
        table.register<String>(handler = { it.length })
        table.register<String>(handler = { it.length })

        assertEquals(2, table.transform(Unit, "ok"))
        assertEquals(1, table.transform(Unit, "a"))
    }

    @Test
    fun testHierarchy() = runBlocking {
        val table = TransformTable<Unit>()
        table.register<String>(handler = { it.length })
        table.register<CharSequence>(handler = { -1 })

        assertEquals(2, table.transform(Unit, "ok"))
        assertEquals(-1, table.transform(Unit, StringBuilder()))
    }

    @Test
    fun testHierarchyX3() = runBlocking {
        val table = TransformTable<Unit>()
        table.register<String>(handler = { if (it == "x3") it else it.length })
        table.register<CharSequence>(handler = { -1 })

        assertEquals(2, table.transform(Unit, "ok"))
        assertEquals(-1, table.transform(Unit, StringBuilder()))
        assertEquals(-1, table.transform(Unit, "x3"))
    }

    @Test
    fun testHierarchyX3Order() = runBlocking {
        val table = TransformTable<Unit>()
        val order = mutableListOf<String>()
        table.register<String>(handler = { order.add("first"); it })
        table.register<CharSequence>(handler = { order.add("second"); it })

        assertEquals("ok", table.transform(Unit, "ok"))

        assertEquals(listOf("first", "second"), order)
    }

    @Test
    fun testNothingRegistered() = runBlocking {
        val table = TransformTable<Unit>()
        assertEquals("ok", table.transform(Unit, "ok"))
        assertEquals(1, table.transform(Unit, 1))
    }

    @Test
    fun testUnknownType() = runBlocking {
        val table = TransformTable<Unit>()
        table.register<CharSequence>(handler = { -1 })

        assertEquals(1, table.transform(Unit, 1))
    }

    @Test
    fun testNobodyCanHandle() = runBlocking {
        val table = TransformTable<Unit>()
        table.register<String>(handler = { it })
        table.register<String>(handler = { it })
        table.register<CharSequence>(handler = { it })

        assertEquals("ok", table.transform(Unit, "ok"))
    }

    @Test
    fun testCycle() = runBlocking {
        val table = TransformTable<Unit>()
        table.register<CharSequence>(handler = { CharBuffer.wrap(it) })

        val obj = "ok"
        val result = table.transform(Unit, obj)

        assertFalse { obj === result }
        assertEquals(obj, result.toString())
    }

    @Test
    fun testCycle2() = runBlocking {
        val table = TransformTable<Unit>()
        table.register<Int>(handler = { it + 1 })

        assertEquals(2, table.transform(Unit, 1))
    }

    @Test
    fun testMultipleHandlersChain() = runBlocking {
        val table = TransformTable<Unit>()
        table.register<Int>(handler = { it + 1 })
        table.register<Int>(handler = { it + 1 })
        table.register<Int>(handler = { it + 1 })

        assertEquals(3, table.transform(Unit, 0))
    }

    @Test
    fun testPredicate() = runBlocking {
        val table = TransformTable<Unit>()
        table.register<Int>(predicate = { it >= 0 }, handler = { it + 1 })

        assertEquals(1, table.transform(Unit, 0))
        assertEquals(2, table.transform(Unit, 1))
        assertEquals(-1, table.transform(Unit, -1))
    }

    @Test
    fun testPredicateCycle() = runBlocking {
        val table = TransformTable<Unit>()
        table.register<Int>(predicate = { it > 0 }, handler = { it + 1 })
        table.register<Int>(handler = { it + 1 })

        assertEquals(2, table.transform(Unit, 0))
        assertEquals(0, table.transform(Unit, -1))
    }

    @Test
    fun testRhombus() = runBlocking {
        val table = TransformTable<Unit>()
        val events = ArrayList<String>()

        table.register<B> { events.add("B"); it }
        table.register<I> { events.add("I"); it }
        table.register<L> { events.add("L"); it }
        table.register<R> { events.add("R"); it }

        table.transform(Unit, BO)
        assertEquals("BLRI", events.joinToString("").replace("RL", "LR"))

        events.clear()
        table.register<BO> { events.add("BO"); it }
        table.transform(Unit, BO)
        assertEquals("BO,B,L,R,I", events.joinToString(",").replace("R,L", "L,R"))
    }

    @Test
    fun testTablesInheritance1() = runBlocking {
        val table = TransformTable<Unit>()
        val subTable = TransformTable(table)

        table.register<CharSequence> { it.length }
        subTable.register<String> { -it.length }

        assertEquals(-2, subTable.transform(Unit, "OK"))
        assertEquals(2, subTable.transform(Unit, StringBuilder("OK")))
    }

    @Test
    fun testTablesInheritance2() = runBlocking {
        val table = TransformTable<Unit>()
        val subTable = TransformTable(table)

        table.register<String> { it.length }
        subTable.register<CharSequence> { -it.length }

        assertEquals(-2, subTable.transform(Unit, "OK"))
        assertEquals(-2, subTable.transform(Unit, StringBuilder("OK")))
    }

    @Test
    fun testTablesInheritance3() = runBlocking {
        val table = TransformTable<Unit>()
        val subTable = TransformTable(table)

        table.register<Int> { it.toString()+"x" }
        subTable.register<CharSequence> { -it.length }

        assertEquals("-2x", subTable.transform(Unit, "OK"))
    }

    @Test
    fun testTablesInheritance4() = runBlocking {
        val table = TransformTable<Unit>()
        val subTable = TransformTable(table)
        val moreTable = TransformTable(subTable)

        table.register<Int> { it.toString()+"x" }
        subTable.register<CharSequence> { -it.length }
        moreTable.register<Float> { it.toString() }

        assertEquals("-4x", moreTable.transform(Unit, 1.54f))
    }

    @Test
    fun testTablesInheritanceWithModification() = runBlocking {
        val table = TransformTable<Unit>()
        val subTable = TransformTable(table)

        table.register<CharSequence> { it.length }
        subTable.register<String> { -it.length }

        assertEquals(-2, subTable.transform(Unit, "OK"))
        assertEquals(2, subTable.transform(Unit, StringBuilder("OK")))

        table.register<StringBuilder> { 101 }
        assertEquals(101, subTable.transform(Unit, StringBuilder("OK")))
    }

    @Test
    fun testLoop() = runBlocking {
        val table = TransformTable<Unit>()
        table.register<String> { it + "." }

        assertEquals("OK.", table.transform(Unit, "OK"))
    }

    interface I
    interface L : I
    interface R : I
    interface B : L, R
    object BO : B
}
