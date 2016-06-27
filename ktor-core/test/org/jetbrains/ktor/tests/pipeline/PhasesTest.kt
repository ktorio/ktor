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
        val phases1 = PipelinePhases<String>(a, b)
        val phases2 = PipelinePhases<String>(c)
        phases1.merge(phases2)
        assertEquals(listOf(a, b, c), phases1.items)
    }

    @Test
    fun testNaturalOrderMerge2() {
        val phases1 = PipelinePhases<String>(a)
        phases1.add(b)
        val phases2 = PipelinePhases<String>(c)
        phases1.merge(phases2)
        assertEquals(listOf(a, b, c), phases1.items)
    }

    @Test
    fun testInsertAfterMerge() {
        val phases1 = PipelinePhases<String>(a)
        val phases2 = PipelinePhases<String>(c)
        phases2.insertAfter(c, b)
        phases1.merge(phases2)
        assertEquals(listOf(a, c, b), phases1.items)
    }

    @Test
    fun testInsertBeforeMerge() {
        val phases1 = PipelinePhases<String>(c, a)
        val phases2 = PipelinePhases<String>(c)
        phases2.insertBefore(c, b)
        phases1.merge(phases2)
        assertEquals(listOf(b, c, a), phases1.items)
    }
}