package io.ktor.tests.utils

import io.ktor.util.pipeline.*
import kotlin.coroutines.*
import kotlin.test.*

@Suppress("KDocMissingDocumentation")
class PipelineContractsTest {
    private var v = 0
    private val phase1 = PipelinePhase("A")
    private val phase2 = PipelinePhase("B")
    private val interceptor1: PipelineInterceptor<Unit, Unit> = { v = 1 }
    private val interceptor2: PipelineInterceptor<Unit, Unit> = { v = 2 }

    @Test
    fun testMergeEmpty() {
        val first = Pipeline<Unit, Unit>(phase1)
        val second = Pipeline<Unit, Unit>(phase1)

        second.merge(first)
        assertTrue { first.isEmpty }
        assertTrue { second.isEmpty }

        assertSame(first.interceptorsForTests(), second.interceptorsForTests())
    }

    @Test
    fun testMergeSingle() {
        val first = Pipeline<Unit, Unit>(phase1)
        val second = Pipeline<Unit, Unit>(phase1)

        first.intercept(phase1) {}

        second.merge(first)

        assertSame(first.interceptorsForTests(), second.interceptorsForTests())
        assertSame(first.interceptorsForTests(), first.phaseInterceptors(phase1))
    }

    @Test
    fun testModifyAfterMerge() {
        val first = Pipeline<Unit, Unit>(phase1)
        val second = Pipeline<Unit, Unit>(phase1)

        first.intercept(phase1, interceptor1)

        second.merge(first)

        first.intercept(phase1, interceptor2)

        assertNotSame(first.interceptorsForTests(), second.interceptorsForTests())
        assertTrue { interceptor2 !in second.phaseInterceptors(phase1) }
        assertTrue { interceptor2 !in second.interceptorsForTests() }
    }

    @Test
    fun testLastPhase() {
        val first = Pipeline<Unit, Unit>(phase1, phase2)
        first.intercept(phase1, interceptor1)
        first.intercept(phase2, interceptor2)

        val before = first.interceptorsForTests()

        first.intercept(phase2, interceptor2)
        // adding an interceptor to the last phase shouldn't reallocate unshared list

        val after = first.interceptorsForTests()

        assertSame(before, after)

        // intercepting earlier phase should

        first.intercept(phase1, interceptor1)

        assertNotSame(before, first.interceptorsForTests())

        val second = Pipeline<Unit, Unit>(phase1, phase2)
        second.merge(first)
    }

    @Test
    fun testLastPhaseThenMerge() {
        val first = Pipeline<Unit, Unit>(phase1, phase2)
        first.intercept(phase1, interceptor1)
        first.intercept(phase2, interceptor2)

        val before = first.interceptorsForTests()

        first.intercept(phase2, interceptor2)
        // adding an interceptor to the last phase shouldn't reallocate unshared list

        val after = first.interceptorsForTests()

        assertSame(before, after)

        val second = Pipeline<Unit, Unit>(phase1, phase2)
        second.merge(first)

        // it should be shared during merge
        assertSame(before, second.interceptorsForTests())

        // intercepting first should reallocate
        first.intercept(phase2, interceptor2)
        assertNotSame(first.interceptorsForTests(), second.interceptorsForTests())
    }

    private fun Pipeline<Unit, Unit>.createContext() = createContext(Unit, Unit, EmptyCoroutineContext)
}
