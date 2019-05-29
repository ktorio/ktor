/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.cio

import io.ktor.http.cio.internals.*
import kotlinx.coroutines.*
import java.lang.Exception
import java.lang.UnsupportedOperationException
import java.time.*
import kotlin.coroutines.*
import kotlin.test.*

class WeakTimeoutQueueTest {
    private val testClock = TestClock(0L)
    private val q = WeakTimeoutQueue(1000L, clock = { testClock.millis() })

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
        } catch (expected: CancellationException) {
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

            @Suppress("UNREACHABLE_CODE")
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

            @Suppress("UNREACHABLE_CODE")
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
