/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.ktor.network.util.*
import io.ktor.test.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.io.IOException
import platform.posix.*
import kotlin.coroutines.*
import kotlin.test.*

class SelectorHelperTest {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun `cancelled selection does not fail the selector`() = runTest {
        val failure = CompletableDeferred<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, cause ->
                failure.complete(cause)
            }
        )
        val selector = SelectorHelper()
        val selection = launch(start = CoroutineStart.UNDISPATCHED) {
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.cancel()
                assertTrue(selector.interest(EventInfo(-1, SelectInterest.READ, continuation)))
            }
        }
        selection.join()

        initSocketsIfNeeded()
        val descriptor = ktor_socket(AF_INET.convert(), SOCK_STREAM, 0).check()
        val closeResult = CompletableDeferred<Result<Unit>>()
        assertTrue(
            selector.interest(
                EventInfo(
                    descriptor,
                    SelectInterest.CLOSE,
                    Continuation(EmptyCoroutineContext) {
                        closeResult.complete(it)
                    }
                )
            )
        )
        selector.notifyClosed(descriptor)

        val job = selector.start(scope)
        job.invokeOnCompletion { cause ->
            if (cause != null) closeResult.complete(Result.failure(cause))
        }
        try {
            closeResult.await().getOrThrow()
            selector.requestTermination()
            job.join()
            assertFalse(job.isCancelled)
            assertFalse(failure.isCompleted)
        } finally {
            if (job.isActive) selector.requestTermination()
            scope.cancel()
        }
    }

    @Test
    fun `selector failure resumes pending selections`() = runTest {
        val failure = CompletableDeferred<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, cause ->
                failure.complete(cause)
            }
        )
        val selector = SelectorHelper()
        val readResult = CompletableDeferred<Result<Unit>>()
        val closeResult = CompletableDeferred<Result<Unit>>()
        assertTrue(
            selector.interest(
                EventInfo(
                    -1,
                    SelectInterest.READ,
                    Continuation(EmptyCoroutineContext) {
                        readResult.complete(it)
                    }
                )
            )
        )
        assertTrue(
            selector.interest(
                EventInfo(
                    -1,
                    SelectInterest.CLOSE,
                    Continuation(EmptyCoroutineContext) {
                        closeResult.complete(it)
                    }
                )
            )
        )

        val job = selector.start(scope)
        try {
            job.join()
            assertTrue(job.isCancelled)
            assertTrue(failure.isCompleted)
            assertTrue(readResult.isCompleted, "A failed selector must resume pending reads")
            assertTrue(closeResult.isCompleted, "A failed selector must resume pending closes")
            assertIs<IOException>(readResult.await().exceptionOrNull())
            assertIs<IOException>(closeResult.await().exceptionOrNull())
        } finally {
            if (job.isActive) selector.requestTermination()
            scope.cancel()
        }
    }
}
