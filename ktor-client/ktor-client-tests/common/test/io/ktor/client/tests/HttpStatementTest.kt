/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.client.tests.utils.*
import io.ktor.test.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.io.readByteArray
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * Bound on the streaming tests below, which hang rather than fail when cancellation isn't honoured.
 *
 * Passed to `clientTests` so it configures the `runTest` timeout: a test that blows it is reported
 * with a coroutine dump of what was still running, where an inner `withTimeout` would instead
 * surface as an unrelated "wrong exception type" assertion failure.
 */
private val GUARD_TIMEOUT = 15.seconds

class HttpStatementTest : ClientLoader() {

    @Test
    @Ignore
    fun testExecute() = clientTests {
        test { client ->
            client.prepareGet("$TEST_SERVER/content/stream").execute {
                val expected = buildPacket {
                    repeat(42) {
                        writeInt(42)
                    }
                }.readByteArray(42)

                val actual = it.readBytes(42)

                assertArrayEquals("Invalid content", expected, actual)
            }

            val response = client.prepareGet("$TEST_SERVER/content/hello").execute()
            assertEquals("hello", response.body())
        }
    }

    @Test
    fun testGZipFromSavedResponse() = clientTests(except("native:CIO", "web:CIO", "WinHttp")) {
        config {
            ContentEncoding {
                gzip()
            }
        }

        test { client ->
            val response = client.get("$TEST_SERVER/compression/gzip")
            assertEventually("the response job to complete") { response.coroutineContext[Job]!!.isCompleted }

            val content = response.body<String>()
            assertEquals("Compressed response!", content)
        }
    }

    @Test
    fun testJobFinishedAfterResponseRead() = clientTests {
        test { client ->
            client.prepareGet("$TEST_SERVER/content/hello").execute().apply {
                assertEventually("the call job to complete") { call.coroutineContext.job.isCompleted }
            }

            client.prepareGet("$TEST_SERVER/content/hello").execute {
                assertFalse(it.call.coroutineContext.job.isCompleted)
                it
            }.apply {
                assertEventually("the call job to complete after the block") {
                    call.coroutineContext.job.isCompleted
                }
            }
        }
    }

    // Darwin/DarwinLegacy: NSURLSession buffers the first 512 bytes before calling didReceiveResponse/didReceiveData,
    // so the test times out waiting for enough data to arrive unless the content type is octet/stream or application/json.
    // See: https://developer.apple.com/forums/thread/64875
    @Test
    fun testStreamingResponseExceptionCancelsImmediately() = clientTests(timeout = GUARD_TIMEOUT) {
        test { client ->
            val exception = assertFailsWith<IllegalStateException> {
                client.prepareGet("$TEST_SERVER/content/stream?delay=60000").execute {
                    // Headers are received, throw exception while waiting for the body
                    throw IllegalStateException("Test exception from execute block")
                }
            }
            assertEquals("Test exception from execute block", exception.message)
        }
    }

    // The Android engine reads the body with a blocking call that doesn't honour cancellation, so
    // throwing from the block leaves the streaming read hanging until the guard fires and the test
    // body never completes. Only the Android branch is quarantined; the other engines — and every
    // Native/JS/Wasm target, where Android can't even be selected — keep the coverage.
    @Test
    fun testStreamingResponseExceptionInBodyCancelsImmediately() =
        clientTests(except("Android"), timeout = GUARD_TIMEOUT) { throwFromStreamingBodyBlock() }

    @Flaky("KTOR-8570")
    @Test
    fun testStreamingResponseExceptionInBodyCancelsImmediately_flaky() =
        clientTests(only("Android"), timeout = GUARD_TIMEOUT) { throwFromStreamingBodyBlock() }

    private fun TestClientBuilder<*>.throwFromStreamingBodyBlock() {
        test { client ->
            val exception = assertFailsWith<IllegalStateException> {
                client.prepareGet("$TEST_SERVER/content/stream?delay=60000").body<ByteReadChannel, Unit> {
                    // Throw exception while a channel is open
                    throw IllegalStateException("Test exception from body block")
                }
            }
            assertEquals("Test exception from body block", exception.message)
        }
    }
}
