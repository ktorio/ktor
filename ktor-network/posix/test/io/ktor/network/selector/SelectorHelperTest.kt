/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.ktor.network.sockets.*
import io.ktor.network.util.*
import io.ktor.test.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.io.IOException
import platform.posix.*
import kotlin.coroutines.*
import kotlin.test.*

class SelectorHelperTest {
    @Test
    fun `cancelled selection does not fail the selector`() = runTest {
        val failure = CompletableDeferred<Throwable>()
        val scope = selectorScope(failure)
        val selector = SelectorHelper()
        val selection = launch(start = CoroutineStart.UNDISPATCHED) {
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.cancel()
                assertTrue(selector.interest(EventInfo(-1, SelectInterest.READ, continuation)))
            }
        }
        selection.join()

        val descriptor = readableUdpSocket()
        val readResult = CompletableDeferred<Result<Unit>>()
        assertTrue(selector.interest(EventInfo(descriptor, SelectInterest.READ, continuationOf(readResult))))

        val job = selector.start(scope)
        job.invokeOnCompletion { cause ->
            if (cause != null) readResult.complete(Result.failure(cause))
        }
        try {
            readResult.await().getOrThrow()
            selector.notifyClosed(descriptor)
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
        val scope = selectorScope(failure)
        val selector = SelectorHelper()
        val readResult = CompletableDeferred<Result<Unit>>()
        val closeResult = CompletableDeferred<Result<Unit>>()
        assertTrue(selector.interest(EventInfo(-1, SelectInterest.READ, continuationOf(readResult))))
        assertTrue(selector.interest(EventInfo(-1, SelectInterest.CLOSE, continuationOf(closeResult))))

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

    private fun selectorScope(failure: CompletableDeferred<Throwable>): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, cause ->
            failure.complete(cause)
        }
    )

    private fun continuationOf(result: CompletableDeferred<Result<Unit>>): Continuation<Unit> =
        Continuation(EmptyCoroutineContext) { result.complete(it) }

    @OptIn(ExperimentalForeignApi::class)
    private fun readableUdpSocket(): Int {
        initSocketsIfNeeded()
        val descriptor = ktor_socket(AF_INET.convert(), SOCK_DGRAM, 0).check()
        InetSocketAddress("127.0.0.1", 0).address.nativeAddress { pointer, size ->
            ktor_bind(descriptor, pointer, size).check()
        }
        getLocalAddress(descriptor).nativeAddress { pointer, size ->
            ktor_connect(descriptor, pointer, size).check()
        }
        byteArrayOf(1).usePinned { data ->
            assertEquals(1, ktor_send(descriptor, data.addressOf(0), 1, 0))
        }
        return descriptor
    }
}
