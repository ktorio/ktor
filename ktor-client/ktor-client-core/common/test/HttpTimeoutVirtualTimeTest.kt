/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class HttpTimeoutVirtualTimeTest {

    @Test
    fun `request timeout respects virtual time in runTest`() = runTest(timeout = 5.seconds) {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    awaitCancellation()
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
            }
        }

        try {
            assertFailsWith<HttpRequestTimeoutException> {
                client.get("http://localhost/test")
            }
        } finally {
            client.close()
        }
    }
}
