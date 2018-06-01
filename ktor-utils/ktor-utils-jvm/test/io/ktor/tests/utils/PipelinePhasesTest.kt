package io.ktor.tests.utils

import io.ktor.pipeline.*
import org.junit.Test
import kotlin.test.*

class PipelinePhasesTest {
    val a = PipelinePhase("a")
    val b = PipelinePhase("b")
    val c = PipelinePhase("c")

    @Test
    fun testNaturalOrderMerge() {
        val phases1 = Pipeline<String, Unit>(a, b)
        val phases2 = Pipeline<String, Unit>(c)
        phases1.merge(phases2)
        assertEquals(listOf(a, b, c), phases1.items)
    }

    @Test
    fun testNaturalOrderMerge2() {
        val phases1 = Pipeline<String, Unit>(a)
        phases1.addPhase(b)
        val phases2 = Pipeline<String, Unit>(c)
        phases1.merge(phases2)
        assertEquals(listOf(a, b, c), phases1.items)
    }

    @Test
    fun testInsertAfterMerge() {
        val phases1 = Pipeline<String, Unit>(a)
        val phases2 = Pipeline<String, Unit>(c)
        phases2.insertPhaseAfter(c, b)
        phases1.merge(phases2)
        assertEquals(listOf(a, c, b), phases1.items)
    }

    @Test
    fun testInsertBeforeMerge() {
        val phases1 = Pipeline<String, Unit>(c, a)
        val phases2 = Pipeline<String, Unit>(c)
        phases2.insertPhaseBefore(c, b)
        phases1.merge(phases2)
        assertEquals(listOf(b, c, a), phases1.items)
    }
}