/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.server.netty.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

class NettyRequestCoroutineTest {
    @Test
    fun `cancellation before executor start still waits for body cleanup`() {
        val parent = SupervisorJob()
        val request = NettyRequestCoroutine(parent + Dispatchers.Unconfined)
        var queued: Runnable? = null
        var cleanedUp = false
        request.launchOn(java.util.concurrent.Executor { queued = it }) {
            try {
                coroutineContext.ensureActive()
            } finally {
                cleanedUp = true
            }
        }
        parent.cancel()
        assertFalse(request.isCompleted)
        assertFalse(parent.isCompleted)

        assertFalse(cleanedUp)
        queued!!.run()
        assertTrue(cleanedUp)
        assertTrue(request.isCompleted)
        assertTrue(parent.isCompleted)
    }

    @Test
    fun `cancellation waits for request cleanup`() {
        val parent = SupervisorJob()
        val request = NettyRequestCoroutine(parent + Dispatchers.Unconfined)
        lateinit var cleanup: Continuation<Unit>
        request.launchOn(java.util.concurrent.Executor { it.run() }) {
            try {
                awaitCancellation()
            } finally {
                suspendCoroutine<Unit> { cleanup = it }
            }
        }

        parent.cancel()
        assertFalse(request.isCompleted)
        assertFalse(parent.isCompleted)
        cleanup.resume(Unit)
        assertTrue(request.isCompleted)
        assertTrue(parent.isCompleted)
    }

    @Test
    fun `completion waits for request children`() {
        val parent = SupervisorJob()
        val request = NettyRequestCoroutine(parent + Dispatchers.Unconfined)
        val childFinished = CompletableDeferred<Unit>()
        request.launchOn(java.util.concurrent.Executor { it.run() }) {
            launch { childFinished.await() }
        }
        parent.complete()
        assertFalse(request.isCompleted)
        assertFalse(parent.isCompleted)
        childFinished.complete(Unit)
        assertTrue(request.isCompleted)
        assertTrue(parent.isCompleted)
    }

    @Test
    fun `initial execution installs and restores thread context`() {
        val local = ThreadLocal.withInitial { "executor" }
        val parent = SupervisorJob()
        val request = NettyRequestCoroutine(parent + local.asContextElement("request"))
        var observed: String? = null
        request.launchOn(java.util.concurrent.Executor { it.run() }) {
            observed = local.get()
        }
        assertEquals("request", observed)
        assertEquals("executor", local.get())
        assertTrue(request.isCompleted)
        parent.complete()
    }

    @Test
    fun `failures are reported without cancelling sibling requests`() {
        for (suspendFirst in listOf(false, true)) {
            val parent = SupervisorJob()
            val failure = IllegalStateException("Request failed")
            var reported: Throwable? = null
            val handler = CoroutineExceptionHandler { _, error -> reported = error }
            val request = NettyRequestCoroutine(parent + Dispatchers.Unconfined + handler)
            val sibling = Job(parent)
            lateinit var continuation: Continuation<Unit>
            request.launchOn(java.util.concurrent.Executor { it.run() }) {
                if (suspendFirst) suspendCoroutine<Unit> { continuation = it }
                throw failure
            }
            if (suspendFirst) continuation.resume(Unit)
            assertTrue(request.isCancelled)
            assertTrue(request.isCompleted)
            assertSame(failure, reported)
            assertTrue(parent.isActive)
            assertTrue(sibling.isActive)
            sibling.complete()
            parent.complete()
        }
    }

    @Test
    fun `rejected dispatch completes as cancellation without reporting failure`() {
        val parent = SupervisorJob()
        var reported: Throwable? = null
        val request = NettyRequestCoroutine(parent + CoroutineExceptionHandler { _, error -> reported = error })
        val rejection = java.util.concurrent.RejectedExecutionException("executor stopped")
        val failure = assertFailsWith<java.util.concurrent.RejectedExecutionException> {
            request.launchOn(java.util.concurrent.Executor { throw rejection }) { fail("Must not execute") }
        }
        assertSame(rejection, failure)
        assertFalse(request.isCompleted)
        request.resumeWith(Result.failure(failure.asCallCompletion()))
        assertTrue(request.isCancelled)
        assertTrue(request.isCompleted)
        assertNull(reported)
        parent.complete()
        assertTrue(parent.isCompleted)
    }
}
