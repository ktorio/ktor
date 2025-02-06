/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlin.test.*

private const val TEST_URL = "$TEST_SERVER/timeout"

class HttpTimeoutTest : ClientLoader() {
    @Test
    fun testGet() = clientTests {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 1000 }
        }

        test { client ->
            val response = client.get("$TEST_URL/with-delay") {
                parameter("delay", 20)
            }.body<String>()
            assertEquals("Text", response)
        }
    }

    @Test
    fun testGetWithExceptionAndTryAgain() = clientTests {
        config {
            expectSuccess = true
        }
        test { client ->
            val requestBuilder = HttpRequestBuilder().apply {
                method = HttpMethod.Get
                url("$TEST_URL/404")
                parameter("delay", 20)
            }

            val job = requestBuilder.executionContext
            assertTrue { job.isActive }

            assertFails { client.request(requestBuilder).body<String>() }
            assertTrue { job.isActive }

            requestBuilder.url("$TEST_URL/with-delay")

            val response = client.request(requestBuilder).body<String>()

            assertEquals("Text", response)
            assertTrue { job.isActive }
        }
    }

    @Test
    fun testWithExternalTimeout() = clientTests(except("Android")) {
        config {
            install(HttpTimeout)
        }

        test { client ->
            val requestBuilder = HttpRequestBuilder().apply {
                method = HttpMethod.Get
                url("$TEST_URL/with-delay")
                parameter("delay", 60000)
            }

            val exception = assertFails {
                withTimeout(500) {
                    client.request(requestBuilder).body<String>()
                }
            }

            assertTrue { exception is TimeoutCancellationException }
            assertTrue { requestBuilder.executionContext.getActiveChildren().none() }
        }
    }

    @Test
    fun testHead() = clientTests {
        config {
            install(HttpTimeout)
        }

        test { client ->
            val response = client.head("$TEST_URL/with-delay?delay=10")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun testHeadWithTimeout() = clientTests {
        config {
            install(HttpTimeout) {
                requestTimeoutMillis = 500
            }
        }

        test { client ->
            assertFailsWith<HttpRequestTimeoutException> {
                client.head("$TEST_URL/with-delay?delay=1000")
            }
        }
    }

    @Test
    fun testGetWithCancellation() = clientTests {
        config {
            install(HttpTimeout) {
                requestTimeoutMillis = 5000
            }

            test { client ->
                val requestBuilder = HttpRequestBuilder().apply {
                    method = HttpMethod.Get
                    url("$TEST_URL/with-stream")
                    parameter("delay", 7000)
                }

                client.prepareRequest(requestBuilder).body<ByteReadChannel>().cancel()

                waitForCondition("all children to be cancelled") {
                    requestBuilder.executionContext.getActiveChildren().none()
                }
            }
        }
    }

    @Test
    fun testGetRequestTimeout() = clientTests {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 10 }
        }

        test { client ->
            assertFails {
                client.get("$TEST_URL/with-delay") {
                    parameter("delay", 5000)
                }.body<String>()
            }
        }
    }

    @Test
    fun testGetRequestTimeoutPerRequestAttributes() = clientTests {
        config {
            install(HttpTimeout)
        }

        test { client ->
            assertFails {
                client.get("$TEST_URL/with-delay") {
                    parameter("delay", 5000)

                    timeout { requestTimeoutMillis = 10 }
                }.body<String>()
            }
        }
    }

    @Test
    fun testGetWithSeparateReceive() = clientTests {
//      https://youtrack.jetbrains.com/issue/KTOR-7847/Investigate-Flaky-timeout-tests-on-linuxX64
        if (PlatformUtils.IS_NATIVE) return@clientTests

        config {
            install(HttpTimeout) { requestTimeoutMillis = 2000 }
        }

        test { client ->
            val response = client.request("$TEST_URL/with-delay") {
                method = HttpMethod.Get
                parameter("delay", 10)
            }
            val result: String = response.body()

            assertEquals("Text", result)
        }
    }

    @Test
    fun testGetWithSeparateReceivePerRequestAttributes() = clientTests {
//      https://youtrack.jetbrains.com/issue/KTOR-7847/Investigate-Flaky-timeout-tests-on-linuxX64
        if (PlatformUtils.IS_NATIVE) return@clientTests

        config {
            install(HttpTimeout)
        }

        test { client ->
            val response = client.request("$TEST_URL/with-delay") {
                method = HttpMethod.Get
                parameter("delay", 10)

                timeout { requestTimeoutMillis = 1000 }
            }
            val result: String = response.body()

            assertEquals("Text", result)
        }
    }

    @Test
    fun testGetRequestTimeoutWithSeparateReceive() = clientTests(except("Js"), retries = 5) {
//      https://youtrack.jetbrains.com/issue/KTOR-7847/Investigate-Flaky-timeout-tests-on-linuxX64
        if (PlatformUtils.IS_NATIVE) return@clientTests

        config {
            install(HttpTimeout) { requestTimeoutMillis = 2000 }
        }

        test { client ->
            val response = client.prepareRequest("$TEST_URL/with-stream") {
                method = HttpMethod.Get
                parameter("delay", 500)
            }.body<ByteReadChannel>()

            assertFailsWith<CancellationException> {
                response.readUTF8Line()
            }
        }
    }

    @Test
    fun testGetRequestTimeoutWithSeparateReceivePerRequestAttributes() = clientTests(
        except("Js", "Curl", "Darwin", "DarwinLegacy")
    ) {
        config {
            install(HttpTimeout)
        }

        test { client ->
            val response = client.prepareRequest("$TEST_URL/with-stream") {
                method = HttpMethod.Get
                parameter("delay", 10000)

                timeout { requestTimeoutMillis = 2000 }
            }.body<ByteReadChannel>()
            assertFailsWith<CancellationException> {
                response.readUTF8Line()
            }
        }
    }

    @Test
    fun testGetAfterTimeout() = clientTests(except("Curl", "Js", "Darwin", "DarwinLegacy")) {
        config {
            install(HttpTimeout)
        }

        test { client ->
            val response = client.prepareGet("$TEST_URL/with-stream") {
                parameter("delay", 10000)
                timeout { requestTimeoutMillis = 1000 }
            }.body<ByteReadChannel>()
            assertFailsWith<CancellationException> {
                response.readUTF8Line()
            }
            val result = client.get("$TEST_URL/with-delay?delay=1") {
                timeout { requestTimeoutMillis = 10000 }
            }.bodyAsText()
            assertEquals("Text", result)
        }
    }

    @Test
    fun testGetStream() = clientTests(retries = 10) {
//        https://youtrack.jetbrains.com/issue/KTOR-7847/Investigate-Flaky-timeout-tests-on-linuxX64
        if (PlatformUtils.IS_NATIVE) return@clientTests

        config {
            install(HttpTimeout) { requestTimeoutMillis = 1000 }
        }

        test { client ->
            val responseBody: String = client.get("$TEST_URL/with-stream") {
                parameter("delay", 10)
            }.body()

            assertEquals("Text", responseBody)
        }
    }

    @Test
    fun testGetStreamRequestTimeout() = clientTests {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 1000 }
        }

        test { client ->
            assertFailsWith<IOException> {
                client.get("$TEST_URL/with-stream") {
                    parameter("delay", 4000)
                }.body<ByteArray>()
            }
        }
    }

    @Test
    fun testGetStreamRequestTimeoutPerRequestAttributes() = clientTests {
        config {
            install(HttpTimeout)
        }

        test { client ->
            assertFailsWith<IOException> {
                client.get("$TEST_URL/with-stream") {
                    parameter("delay", 400)

                    timeout { requestTimeoutMillis = 1000 }
                }.body<ByteArray>()
            }
        }
    }

    // Js can't configure test timeout in browser
    // Fix https://youtrack.jetbrains.com/issue/KTOR-7885
    @Ignore
    @Test
    fun testRedirect() = clientTests(except("Js"), retries = 5) {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 10000 }
        }

        test { client ->
            val response = client.get("$TEST_URL/with-redirect") {
                parameter("delay", 20)
                parameter("count", 2)
            }.body<String>()

            assertEquals("Text", response)
        }
    }

    // Js can't configure test timeout in browser
    @Test
    fun testRedirectPerRequestAttributes() = clientTests(except("Js")) {
        config {
            install(HttpTimeout)
        }

        test { client ->
            val response = client.get("$TEST_URL/with-redirect") {
                parameter("delay", 20)
                parameter("count", 2)

                timeout { requestTimeoutMillis = 10000 }
            }.body<String>()
            assertEquals("Text", response)
        }
    }

    @Test
    fun testRedirectRequestTimeoutOnFirstStep() = clientTests {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 20 }
        }

        test { client ->
            assertFailsWith<HttpRequestTimeoutException> {
                client.get("$TEST_URL/with-redirect") {
                    parameter("delay", 1000)
                    parameter("count", 5)
                }.body<String>()
            }
        }
    }

    @Test
    fun testRedirectRequestTimeoutOnFirstStepPerRequestAttributes() = clientTests {
        config {
            install(HttpTimeout)
        }

        test { client ->
            assertFailsWith<HttpRequestTimeoutException> {
                client.get("$TEST_URL/with-redirect") {
                    parameter("delay", 1000)
                    parameter("count", 5)

                    timeout { requestTimeoutMillis = 20 }
                }.body<String>()
            }
        }
    }

    @Test
    fun testRedirectRequestTimeoutOnSecondStep() = clientTests {
        config {
            install(HttpTimeout) { requestTimeoutMillis = 400 }
        }

        test { client ->
            assertFailsWith<HttpRequestTimeoutException> {
                client.get("$TEST_URL/with-redirect") {
                    parameter("delay", 500)
                    parameter("count", 5)
                }.body<String>()
            }
        }
    }

    @Test
    fun testRedirectRequestTimeoutOnSecondStepPerRequestAttributes() = clientTests {
        config {
            install(HttpTimeout)
        }

        test { client ->
            assertFailsWith<HttpRequestTimeoutException> {
                client.get("$TEST_URL/with-redirect") {
                    parameter("delay", 500)
                    parameter("count", 5)

                    timeout { requestTimeoutMillis = 400 }
                }.body<String>()
            }
        }
    }

    @Test
    fun testConnectionRefusedException() = clientTests(except("Js", "native:*", "jvm/win:*")) {
        config {
            install(HttpTimeout) { connectTimeoutMillis = 1000 }
        }

        test { client ->
            assertFails {
                try {
                    client.get("http://localhost:11").body<String>()
                } catch (_: ConnectTimeoutException) {
                }
            }
        }
    }

    @Test
    fun testSocketTimeoutRead() = clientTests(except("Js", "native:CIO", "Curl", "Java")) {
        config {
            install(HttpTimeout) { socketTimeoutMillis = 1000 }
        }

        test { client ->
            assertFailsWith<IOException> {
                client.get("$TEST_URL/with-stream") {
                    parameter("delay", 5000)
                }.body<String>()
            }
        }
    }

    @Test
    fun testSocketTimeoutReadPerRequestAttributes() = clientTests(
        except("Js", "native:CIO", "Curl", "Java", "Apache5")
    ) {
        config {
            install(HttpTimeout)
        }

        test { client ->
            assertFailsWith<IOException> {
                client.get("$TEST_URL/with-stream") {
                    parameter("delay", 5000)

                    timeout { socketTimeoutMillis = 1000 }
                }.body<String>()
            }
        }
    }

    @Test
    fun testSocketTimeoutWriteFailOnWrite() = clientTests(
        except("Js", "Android", "native:CIO", "web:CIO", "Curl", "Java", "WinHttp")
    ) {
        config {
            install(HttpTimeout) { socketTimeoutMillis = 500 }
        }

        test { client ->
            assertFailsWith<SocketTimeoutException> {
                client.post("$TEST_URL/slow-read") { setBody(makeString(4 * 1024 * 1024)) }
            }
        }
    }

    @Test
    fun testSocketTimeoutWriteFailOnWritePerRequestAttributes() = clientTests(
        except("Js", "Android", "Apache5", "native:CIO", "web:CIO", "Curl", "Java", "WinHttp")
    ) {
        config {
            install(HttpTimeout)
        }

        test { client ->
            assertFailsWith<SocketTimeoutException> {
                client.post("$TEST_URL/slow-read") {
                    setBody(makeString(4 * 1024 * 1024))
                    timeout { socketTimeoutMillis = 500 }
                }
            }
        }
    }

    @Test
    fun testNonPositiveTimeout() {
        assertFailsWith<IllegalArgumentException> {
            HttpTimeoutConfig(
                requestTimeoutMillis = -1
            )
        }
        assertFailsWith<IllegalArgumentException> {
            HttpTimeoutConfig(
                requestTimeoutMillis = 0
            )
        }

        assertFailsWith<IllegalArgumentException> {
            HttpTimeoutConfig(
                socketTimeoutMillis = -1
            )
        }
        assertFailsWith<IllegalArgumentException> {
            HttpTimeoutConfig(
                socketTimeoutMillis = 0
            )
        }

        assertFailsWith<IllegalArgumentException> {
            HttpTimeoutConfig(
                connectTimeoutMillis = -1
            )
        }
        assertFailsWith<IllegalArgumentException> {
            HttpTimeoutConfig(
                connectTimeoutMillis = 0
            )
        }
    }
}
