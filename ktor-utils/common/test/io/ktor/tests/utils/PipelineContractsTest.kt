/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.util.pipeline.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.test.*

@Suppress("KDocMissingDocumentation")
class PipelineContractsTest {
    private var v = 0
    private val phase1 = PipelinePhase("A")
    private val phase2 = PipelinePhase("B")
    private val interceptor1: PipelineInterceptor<Unit, Unit> = { v = 1; checkList.add("1") }
    private val interceptor2: PipelineInterceptor<Unit, Unit> = { v = 2; checkList.add("2") }

    private val checkList = ArrayList<String>()

    @Test
    fun testMergeEmpty() {
        val first = Pipeline<Unit, Unit>(phase1)
        val second = Pipeline<Unit, Unit>(phase1)

        second.merge(first)
        assertTrue { first.isEmpty }
        assertTrue { second.isEmpty }
    }

    @Test
    fun testModifyAfterMerge() {
        val first = Pipeline<Unit, Unit>(phase1)
        val second = Pipeline<Unit, Unit>(phase1)

        first.intercept(phase1, interceptor1)

        second.merge(first)

        first.intercept(phase1, interceptor2)

        second.execute()
        assertEquals(listOf("1", "completed"), checkList)
    }

    @Test
    fun executeEmptyPipelineSmokeTest() {
        val pipeline = Pipeline<Unit, Unit>(phase1)

        pipeline.execute()

        assertEquals(listOf("completed"), checkList)
    }

    @Test
    fun testExecutePipelineSingleSmokeTest() {
        val pipeline = Pipeline<Unit, Unit>(phase1)

        pipeline.intercept(phase1, interceptor1)
        pipeline.execute()

        assertEquals(listOf("1", "completed"), checkList)
    }

    @Test
    fun testExecutePipelineSimpleSmokeTest() {
        val pipeline = Pipeline<Unit, Unit>(phase1)

        pipeline.intercept(phase1, interceptor1)
        pipeline.intercept(phase1, interceptor2)
        pipeline.execute()

        assertEquals(listOf("1", "2", "completed"), checkList)
    }

    @Test
    fun executePipelineSmokeTest() {
        val pipeline = Pipeline<Unit, Unit>(phase1)
        pipeline.intercept(phase1) {
            checkList.add("1")
        }
        pipeline.intercept(phase1) {
            try {
                checkList.add("2")
                proceed()
            } finally {
                checkList.add("3")
            }
        }
        pipeline.intercept(phase1) {
            checkList.add("4")
        }

        pipeline.execute()

        assertEquals(listOf("1", "2", "4", "3", "completed"), checkList)
    }

    @Test
    fun executePipelineWithSuspension() {
        val pipeline = Pipeline<Unit, Unit>(phase1)
        var continuation: Continuation<Unit>? = null

        pipeline.intercept(phase1) {
            checkList.add("1")
        }
        pipeline.intercept(phase1) {
            try {
                checkList.add("2")
                proceed()
            } finally {
                checkList.add("3")
            }
        }
        pipeline.intercept(phase1) {
            checkList.add("4")
            suspendCoroutine<Unit> {
                continuation = it
            }
            checkList.add("5")
        }

        pipeline.execute()

        assertEquals(listOf("1", "2", "4"), checkList)

        continuation!!.resume(Unit)
        assertEquals(listOf("1", "2", "4", "5", "3", "completed"), checkList)
    }

    @Test
    fun executePipelineWithSuspensionAndImmediateResume() {
        val pipeline = Pipeline<Unit, Unit>(phase1)

        pipeline.intercept(phase1) {
            checkList.add("1")
        }
        pipeline.intercept(phase1) {
            try {
                checkList.add("2")
                proceed()
            } finally {
                checkList.add("3")
            }
        }
        pipeline.intercept(phase1) {
            checkList.add("4")
            suspendCoroutineUninterceptedOrReturn<Unit> {
                it.resume(Unit)
                COROUTINE_SUSPENDED
            }
            checkList.add("5")
        }

        pipeline.execute()

        assertEquals(listOf("1", "2", "4", "5", "3", "completed"), checkList)
    }

    @Test
    fun executePipelineWithSuspensionAndNestedProceed() {
        val pipeline = Pipeline<Unit, Unit>(phase1)
        var continuation: Continuation<Unit>? = null

        pipeline.intercept(phase1) {
            checkList.add("1")
        }
        pipeline.intercept(phase1) {
            checkList.add("2")
            suspendCoroutineUninterceptedOrReturn<Unit> {
                continuation = it
                COROUTINE_SUSPENDED
            }
            checkList.add("3")
        }
        pipeline.intercept(phase1) {
            try {
                checkList.add("4")
                proceed()
            } finally {
                checkList.add("5")
            }
        }
        pipeline.intercept(phase1) {
            checkList.add("6")
        }

        pipeline.execute()

        assertEquals(listOf("1", "2"), checkList)

        continuation!!.resume(Unit)

        assertEquals(listOf("1", "2", "3", "4", "6", "5", "completed"), checkList)
    }

    @Test
    fun testExecutePipelineTwiceTest() {
        val pipeline = Pipeline<Unit, Unit>(phase1)

        pipeline.intercept(phase1, interceptor1)
        pipeline.execute()
        pipeline.execute()

        assertEquals(listOf("1", "completed", "1", "completed"), checkList)
    }

    @Test
    fun testExecutePipelineFailureTest() {
        val pipeline = Pipeline<Unit, Unit>(phase1)

        pipeline.intercept(phase1) {
            throw IllegalStateException()
        }

        pipeline.execute()
    }

    @Test
    fun testExecutePipelineCaughtFailureTest() {
        var caught = false
        val pipeline = Pipeline<Unit, Unit>(phase1)

        class MyException : Exception()

        pipeline.intercept(phase1) {
            try {
                proceed()
            } catch (expected: MyException) {
                caught = true
            }
        }
        pipeline.intercept(phase1) {
            throw MyException()
        }

        pipeline.execute()
        assertTrue { caught }
    }

    @Test
    fun testCaching() {
        val pipeline = Pipeline(phase1, listOf(interceptor1))

        val before = pipeline.allInterceptors()
        assertEquals(listOf(interceptor1), before)
        assertSame(before, pipeline.allInterceptors())

        pipeline.intercept(phase1, interceptor2)

        val after = pipeline.allInterceptors()
        assertEquals(listOf(interceptor1, interceptor2), after)
        assertNotSame(before, after)
        assertSame(after, pipeline.allInterceptors())
    }

    @Test
    fun testFastPathMerge() {
        val first = Pipeline<Unit, Unit>()
        val second = Pipeline(phase1, listOf(interceptor1))

        val secondInterceptors = second.allInterceptors()
        first.merge(second)

        assertSame(secondInterceptors, first.allInterceptors())
        assertFalse(first.isEmpty)
        assertEquals(listOf(phase1), first.items)
    }

    @Test
    fun testPhaseAlreadyExists() {
        val pipeline = Pipeline<Unit, Unit>(phase1, phase2)
        pipeline.addPhase(phase1)
        pipeline.insertPhaseAfter(phase1, phase2)
        pipeline.insertPhaseBefore(phase2, phase1)
        assertEquals(listOf(phase1, phase2), pipeline.items)
    }

    private fun Pipeline<Unit, Unit>.execute() {
        val body = suspend {
            execute(Unit, Unit)
        }

        body.startCoroutine(Continuation(EmptyCoroutineContext) {
            checkList.add("completed")
        })
    }
}
