/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class Http2Test<T : HttpClientEngineConfig>(
    factory: HttpClientEngineFactory<T>,
    private val useH2c: Boolean = true,
) : ClientEngineTest<T>(factory) {

    private val testHost = if (useH2c) "http://localhost:8084" else "https://localhost:8089"

    protected open fun T.enableHttp2() {}

    /** Should be overridden in case [useH2c] is set to `false`. */
    protected open fun T.disableCertificateValidation() {
        TODO()
    }

    @Test
    open fun `test protocol version is HTTP 2`() = testClient {
        configureClient()

        test { client ->
            val response = client.get("/")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(HttpProtocolVersion.HTTP_2_0, response.version)
        }
    }

    @Test
    open fun `test pseudo headers are ignored`() = testClient {
        configureClient()

        test { client ->
            val pseudoHeaders = client.get("/").headers.names().filter { it.startsWith(":") }
            assertTrue(pseudoHeaders.isEmpty(), "Expected no pseudo headers, but found: $pseudoHeaders")
        }
    }

    protected fun TestClientBuilder<T>.configureClient() {
        config {
            engine {
                enableHttp2()
                if (!useH2c) disableCertificateValidation()
            }
            defaultRequest { url(testHost) }
        }
    }
}
