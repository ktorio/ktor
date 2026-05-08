/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class AttachToUserJobTest {

    @Test
    fun `attachToUserJob preserves TimeoutCancellationException subtype`(): Unit = runBlocking {
        val callJob = Job()

        assertThrows<TimeoutCancellationException> {
            withTimeout(50) {
                attachToUserJob(callJob)
                awaitCancellation()
            }
        }
    }
}
