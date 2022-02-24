/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.http.cio

import io.ktor.http.cio.internals.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.*
import org.junit.Rule
import java.time.*
import kotlin.test.*

class WeakTimeoutQueueTest {
    private val testClock = TestClock(0L)
    private val q = WeakTimeoutQueue(1000L, clock = { testClock.millis() })

    @get:Rule
    val timeout = CoroutinesTimeout(2000L, true)

    @Test
    fun testNoTimeout() = runBlocking {
        val rc = q.withTimeout<Int> {
            q.process()
            1
        }
        assertEquals(1, rc)
        assertEquals(0, q.count())
    }

    @Test
    fun testNoTimeoutAfterResume() = runBlocking {
        val rc = q.withTimeout<Int> {
            yield()
            q.process()
            1
        }
        assertEquals(1, rc)
        assertEquals(0, q.count())
    }

    @Test
    fun testTimeoutPassed() = runBlocking {
        try {
            q.withTimeout {
                suspendCancellableCoroutine<Unit> {
                    testClock.millis = 1001
                    q.process()
                }
            }
        } catch (expected: CancellationException) {
        }

        assertEquals(0, q.count())
    }

    @Test
    fun testTimeoutAfter() = runBlocking<Unit> {
        q.withTimeout {
        }
        testClock.millis = 1001
        q.process()
        assertEquals(0, q.count())
    }

    @Test
    fun testTimeoutFailed() = runBlocking<Unit> {
        try {
            q.withTimeout {
                throw MyException()
            }

            @Suppress("UNREACHABLE_CODE")
            fail("Should fail before")
        } catch (expected: MyException) {
        }

        assertEquals(0, q.count())
    }

    @Test
    fun testTimeoutFailedViaResume() = runBlocking<Unit> {
        try {
            q.withTimeout {
                yield()
                throw MyException()
            }

            @Suppress("UNREACHABLE_CODE")
            fail("Should fail before")
        } catch (expected: MyException) {
        }

        assertEquals(0, q.count())
    }

    private val caught = atomic(0)
    private val executed = atomic(0)

    @Test
    fun testAlreadyCancelledParent(): Unit = runBlocking(Dispatchers.IO) {
        launch {
            cancel("Already cancelled")

            try {
                q.withTimeout {
                    executed.incrementAndGet()
                }
            } catch (cause: CancellationException) {
                caught.incrementAndGet()
                assertEquals("Already cancelled", cause.message)
            }
        }.join()

        assertEquals(1, caught.value)
        assertEquals(0, executed.value)
        assertEquals(0, q.count())
    }

    private class MyException : Exception()

    private class TestClock(var millis: Long) : Clock() {
        override fun withZone(zone: ZoneId?): Clock {
            throw UnsupportedOperationException()
        }

        override fun getZone(): ZoneId {
            throw UnsupportedOperationException()
        }

        override fun millis(): Long {
            return millis
        }

        override fun instant(): Instant {
            return Instant.ofEpochMilli(millis)
        }
    }
}
