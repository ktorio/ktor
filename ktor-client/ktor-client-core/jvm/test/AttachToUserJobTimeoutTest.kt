/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue

class AttachToUserJobTimeoutTest {

    @Test
    fun `attachToUserJob preserves TimeoutCancellationException subtype`(): Unit = runBlocking {
        val callJob = Job()
        val callJobCause = CompletableDeferred<Throwable?>()
        callJob.invokeOnCompletion { callJobCause.complete(it) }

        try {
            withTimeout(50) {
                attachToUserJob(callJob)
                awaitCancellation()
            }
        } catch (_: TimeoutCancellationException) {
        }

        val cause = callJobCause.await()
        assertTrue(cause is TimeoutCancellationException, "Expected TimeoutCancellationException, was $cause")
    }
}
