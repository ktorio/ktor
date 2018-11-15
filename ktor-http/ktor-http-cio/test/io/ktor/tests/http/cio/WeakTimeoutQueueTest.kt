package io.ktor.tests.http.cio

import io.ktor.http.cio.internals.*
import io.ktor.http.cio.internals.TimeoutCancellationException
import kotlinx.coroutines.*
import java.lang.Exception
import java.lang.UnsupportedOperationException
import java.time.*
import kotlin.coroutines.*
import kotlin.test.*

class WeakTimeoutQueueTest {
    private val testClock = TestClock(0L)
    private val q = WeakTimeoutQueue(1000L, testClock)

    @Test
    fun testNoTimeout() = runBlocking {
        val rc = q.withTimeout<Int> {
            q.process()
            1
        }
        assertEquals(1, rc)
    }

    @Test
    fun testNoTimeoutAfterResume() = runBlocking {
        val rc = q.withTimeout<Int> {
            yield()
            q.process()
            1
        }
        assertEquals(1, rc)
    }

    @Test
    fun testTimeoutPassed() = runBlocking {
        try {
            q.withTimeout {
                suspendCoroutine<Unit> {
                    testClock.millis = 1001
                    q.process()
                }
            }
        } catch (expected: TimeoutCancellationException) {
        }
    }

    @Test
    fun testTimeoutAfter() = runBlocking<Unit> {
        q.withTimeout {
        }
        testClock.millis = 1001
        q.process()
    }

    @Test
    fun testTimeoutFailed() = runBlocking<Unit> {
        try {
            q.withTimeout {
                throw MyException()
            }
            fail("Should fail before")
        } catch (expected: MyException) {
        }
    }

    @Test
    fun testTimeoutFailedViaResume() = runBlocking<Unit> {
        try {
            q.withTimeout {
                yield()
                throw MyException()
            }
            fail("Should fail before")
        } catch (expected: MyException) {
        }
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
