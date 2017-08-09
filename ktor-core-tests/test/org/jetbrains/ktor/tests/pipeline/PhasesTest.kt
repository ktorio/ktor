package org.jetbrains.ktor.tests.pipeline

import org.jetbrains.ktor.pipeline.*
import org.junit.*
import kotlin.test.*

class PhasesTest {
    val a = PipelinePhase("a")
    val b = PipelinePhase("b")
    val c = PipelinePhase("c")

    @Test
    fun testNaturalOrderMerge() {
        val phases1 = Pipeline<String>(a, b)
        val phases2 = Pipeline<String>(c)
        phases1.merge(phases2)
        assertEquals(listOf(a, b, c), phases1.items)
    }

    @Test
    fun testNaturalOrderMerge2() {
        val phases1 = Pipeline<String>(a)
        phases1.addPhase(b)
        val phases2 = Pipeline<String>(c)
        phases1.merge(phases2)
        assertEquals(listOf(a, b, c), phases1.items)
    }

    @Test
    fun testInsertAfterMerge() {
        val phases1 = Pipeline<String>(a)
        val phases2 = Pipeline<String>(c)
        phases2.insertPhaseAfter(c, b)
        phases1.merge(phases2)
        assertEquals(listOf(a, c, b), phases1.items)
    }

    @Test
    fun testInsertBeforeMerge() {
        val phases1 = Pipeline<String>(c, a)
        val phases2 = Pipeline<String>(c)
        phases2.insertPhaseBefore(c, b)
        phases1.merge(phases2)
        assertEquals(listOf(b, c, a), phases1.items)
    }
}