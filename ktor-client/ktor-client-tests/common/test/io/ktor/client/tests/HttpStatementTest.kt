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
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import kotlin.test.*

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
            assertTrue(response.coroutineContext[Job]!!.isCompleted)

            val content = response.body<String>()
            assertEquals("Compressed response!", content)
        }
    }

    @Test
    fun testJobFinishedAfterResponseRead() = clientTests {
        test { client ->
            client.prepareGet("$TEST_SERVER/content/hello").execute().apply {
                assertTrue(call.coroutineContext.job.isCompleted)
            }

            client.prepareGet("$TEST_SERVER/content/hello").execute {
                assertFalse(it.call.coroutineContext.job.isCompleted)
                it
            }.apply {
                assertTrue(call.coroutineContext.job.isCompleted)
            }
        }
    }

    // Darwin/DarwinLegacy: NSURLSession buffers the first 512 bytes before calling didReceiveResponse/didReceiveData,
    // so the test times out waiting for enough data to arrive unless the content type is octet/stream or application/json.
    // See: https://developer.apple.com/forums/thread/64875
    @Test
    fun testStreamingResponseExceptionCancelsImmediately() = clientTests {
        test { client ->
            val exception = assertFailsWith<IllegalStateException> {
                withTimeout(2000) {
                    client.prepareGet("$TEST_SERVER/content/stream?delay=60000").execute {
                        // Headers are received, throw exception while waiting for the body
                        throw IllegalStateException("Test exception from execute block")
                    }
                }
            }
            assertEquals("Test exception from execute block", exception.message)
        }
    }

    @Test
    fun testStreamingResponseExceptionInBodyCancelsImmediately() = clientTests {
        test { client ->
            val exception = assertFailsWith<IllegalStateException> {
                withTimeout(2000) {
                    client.prepareGet("$TEST_SERVER/content/stream?delay=60000").body<ByteReadChannel, Unit> {
                        // Throw exception while a channel is open
                        throw IllegalStateException("Test exception from body block")
                    }
                }
            }
            assertEquals("Test exception from body block", exception.message)
        }
    }
}
