/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.retry.tests

import io.ktor.client.fatures.retry.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import kotlin.random.*
import kotlin.test.*

class RetryTest : ClientLoader() {
    @Test
    fun testConfiguration() = clientTests {
        config {
            retry {
                retries = 8
                retryIntervalInSeconds = 5
            }
        }

        test { client ->
            client.feature(Retry)!!.let { feature ->
                assertEquals(8, feature.retries)
                assertEquals(5, feature.retryIntervalInSeconds)
            }
            assertTrue(Retry.RetryPhase in client.requestPipeline.items)
        }
    }

    @Test
    fun testTestSuite() = clientTests {
        test { client ->
            val id = Random.nextInt(999999)

            repeat(2) {
                assertFails {
                    client.get<String>("$TEST_SERVER/retry/status?id=$id")
                }
            }

            assertEquals("OK, $id", client.get("$TEST_SERVER/retry/status?id=$id"))
        }
    }

    @Test
    fun testWithoutErrors() = clientTests {
        config {
            retry()
        }

        test { client ->
            assertEquals("hello", client.get("$TEST_SERVER/content/hello"))
        }
    }

    @Test
    fun testWithTimeout() = clientTests(skipEngines = listOf("Android")) {
        config {
            retry {
                retries = 4
                retryIntervalInSeconds = 0
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 100
            }
        }

        test { client ->
            assertEquals(4, client.feature(Retry)!!.retries)
            val cause: Retry.RequestRetriesExceededException = try {
                client.get<String>("$TEST_SERVER/timeout/with-delay?delay=99999999")
                error("Should fail")
            } catch (cause: Retry.RequestRetriesExceededException) {
                cause
            }

            assertEquals(4, cause.attempts)
        }
    }

    @Test
    fun testWithUnableToConnect() = clientTests {
        config {
            retry {
                retries = 4
                retryIntervalInSeconds = 0
            }
        }

        test { client ->
            assertEquals(4, client.feature(Retry)!!.retries)
            val cause: Retry.RequestRetriesExceededException = try {
                client.get<String>("http://non-existing-domain")
                error("Should fail")
            } catch (cause: Retry.RequestRetriesExceededException) {
                cause
            }

            assertEquals(4, cause.attempts)
        }
    }

    @Test
    fun testRecoverWithTimeout() = clientTests {
        config {
            retry {
                retries = 4
                retryIntervalInSeconds = 0
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 100
            }
        }

        test { client ->
            val id = Random.nextInt(999999)
            val result = client.get<String>("$TEST_SERVER/retry/status?id=$id")

            assertEquals("OK, $id", result)
        }
    }

    @Test
    fun testRecover() = clientTests {
        config {
            retry {
                retries = 4
                retryIntervalInSeconds = 0
            }
        }

        test { client ->
            val id = Random.nextInt(999999)
            val result = client.get<String>("$TEST_SERVER/retry/status?id=$id")

            assertEquals("OK, $id", result)
            client.close()
        }
    }

    @Test
    fun testRecoverNotEnough() = clientTests {
        config {
            retry {
                retries = 2 // less attempts than required
                retryIntervalInSeconds = 0
            }
        }

        test { client ->
            val id = Random.nextInt(999999)
            assertFailsWith<Retry.RequestRetriesExceededException> {
                client.get<String>("$TEST_SERVER/retry/status?id=$id")
            }
        }
    }

    @Test
    fun testNotFailingWithoutValidation() = clientTests {
        config {
            expectSuccess = false

            retry {
                retries = 1
                retryIntervalInSeconds = 0
            }
        }

        test { client ->
            val id = Random.nextInt(999999)
            val result = client.get<String>("$TEST_SERVER/retry/status?id=$id")

            assertEquals("Failure for $id", result)
        }
    }
}
