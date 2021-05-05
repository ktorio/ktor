/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.util.pipeline.*
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
