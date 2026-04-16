/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.testing

import io.ktor.client.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class TestApplicationCoroutinesTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `check virtual time is supported`() = testApplication(UnconfinedTestDispatcher()) {
        routing {
            get("/") {
                withTimeoutOrNull(20.seconds) {
                    delay(1.hours)
                }
                call.respondText("OK")
            }
        }

        val mark = kotlin.time.TimeSource.Monotonic.markNow()
        client.get("/")
        val elapsed = mark.elapsedNow().inWholeMilliseconds
        assertTrue(elapsed < 5_000, "Expected request to finish quickly, but it took $elapsed ms")
    }
}
