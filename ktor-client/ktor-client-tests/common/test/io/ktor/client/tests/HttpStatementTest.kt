/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HttpStatementTest : ClientLoader(timeout = 5.seconds) {

    @Test
    fun `execute reads a streaming response`() = clientTests {
        test { client ->
            client.prepareStream().execute { response ->
                val expected = buildPacket {
                    repeat(42) {
                        writeInt(42)
                    }
                }.readByteArray(42)

                val actual = response.readBytes(42)

                assertArrayEquals("Invalid content", expected, actual)
            }
        }
    }

    @Test
    fun `execute returns a saved response`() = clientTests {
        test { client ->
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
    fun `reading a compressed response completes its job`() = clientTests(except("native:CIO", "web:CIO", "WinHttp")) {
        config {
            ContentEncoding {
                gzip()
            }
        }

        test { client ->
            val job = client.prepareGet("$TEST_SERVER/compression/gzip").execute { response ->
                assertEquals("Compressed response!", response.body<String>())
                response.coroutineContext.job
            }
            assertTrue(job.isCompleted)
            assertFalse(job.isCancelled)
        }
    }

    @OptIn(InternalAPI::class)
    @Test
    fun `fully reading a delegated response completes its job`() = clientTests {
        config {
            install("new-channel-per-access") {
                receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                    assertContentEquals(byteArrayOf(), response.rawContent.toByteArray())
                    // Multiple layers of delegation
                    val delegated = response.call
                        .replaceResponse { ByteReadChannel("hello".encodeToByteArray()) }
                        .replaceResponse { rawContent }
                    proceedWith(delegated.response)
                }
            }
        }

        test { client ->
            val job = client.prepareGet("$TEST_SERVER/content/empty").execute { response ->
                assertEquals("hello", response.bodyAsText())
                response.coroutineContext.job
            }
            assertTrue(job.isCompleted, "Job should have completed")
            assertFalse(job.isCancelled, "Job should not have cancelled")
        }
    }

    @Test
    fun `cleanup completes a manually saved streaming response`() = clientTests {
        config {
            install("manual-save") {
                receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                    proceedWith(response.call.save().response)
                }
            }
        }

        test { client ->
            val job = client.prepareGet("$TEST_SERVER/content/hello").execute { response ->
                assertEquals("hello", response.bodyAsText())
                response.coroutineContext.job
            }
            assertTrue(job.isCompleted, "Job should have completed")
            assertFalse(job.isCancelled, "Job should not have cancelled")
        }
    }

    @Test
    fun `cleanup does not access delegated content`() = clientTests {
        var contentAccesses = 0
        config {
            install("throwing-response-content") {
                receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                    val result = if (response.call.request.url.encodedPath == "/content/stream") {
                        response.call.replaceResponse {
                            contentAccesses++
                            error("Content access failed")
                        }.response
                    } else {
                        response
                    }
                    proceedWith(result)
                }
            }
        }

        test { client ->
            val responseJob = client.prepareStream(delay = 100.milliseconds).execute { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                response.coroutineContext.job
            }
            assertTrue(responseJob.isCancelled, "Job should have been cancelled")
            assertTrue(responseJob.isCompleted, "Job should have completed")
            assertEquals(0, contentAccesses, "Cleanup accessed delegated content")

            val exception = assertFailsWith<IllegalStateException> {
                client.prepareStream(delay = 1.minutes).execute {
                    throw IllegalStateException("Block failed")
                }
            }
            assertEquals("Block failed", exception.message)
            assertEquals(0, contentAccesses, "Cleanup accessed delegated content")
        }
    }

    @Test
    fun testJobFinishedAfterResponseRead() = clientTests {
        test { client ->
            val saved = client.prepareGet("$TEST_SERVER/content/hello").execute()
            assertTrue(saved.call.coroutineContext.job.isCompleted)
            assertContentEquals("hello".encodeToByteArray(), saved.bodyAsChannel().toByteArray())

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
                client.prepareStream(delay = 1.minutes).execute {
                    // Headers are received, throw exception while waiting for the body
                    throw IllegalStateException("Test exception from execute block")
                }
            }
            assertEquals("Test exception from execute block", exception.message)
        }
    }

    @Test
    fun testStreamingResponseExceptionInBodyCancelsImmediately() = clientTests {
        test { client ->
            val exception = assertFailsWith<IllegalStateException> {
                client.prepareStream(delay = 1.minutes).body<ByteReadChannel, Unit> {
                    // Throw exception while a channel is open
                    throw IllegalStateException("Test exception from body block")
                }
            }
            assertEquals("Test exception from body block", exception.message)
        }
    }

    @Test
    fun `execute returns without reading the body`() = clientTests {
        test { client ->
            val status = client.prepareStream(startDelay = 500.milliseconds, chunkSize = 65536, chunkCount = 32)
                .execute { it.status } // Return from execute immediately, do not read the body
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @OptIn(InternalAPI::class)
    @Test
    fun `execute returns after partially reading rawContent`() = clientTests {
        test { client ->
            client.prepareStream(delay = 1.minutes).execute { response ->
                assertEquals(42, response.rawContent.readInt())
            }
        }
    }

    // KTOR-9954: WinHttp can leave the request handle open if callback removal fails during cleanup.
    @Test
    fun `execute stops the underlying stream after block exit`() = clientTests(except("WinHttp")) {
        test { client ->
            val id = Random.nextInt()

            suspend fun isStreamingActive() =
                client.get("$TEST_SERVER/content/stream/active?id=$id").bodyAsText() == "true"

            val responseJob = client.prepareStream(id = id, delay = 100.milliseconds).execute { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(isStreamingActive())
                response.coroutineContext.job
            }

            val streamStopped = withTimeoutOrNull(2.seconds) {
                while (isStreamingActive()) delay(100.milliseconds)
            } != null
            assertTrue(
                streamStopped,
                "Stream $id remained active after execute: " +
                    "jobCancelled=${responseJob.isCancelled}, " +
                    "jobCompleted=${responseJob.isCompleted}"
            )
        }
    }

    // KTOR-9938: Apache5 doesn't close the consumer job.
    // KTOR-9952: CIO doesn't abort the exchange when rawContent is canceled inside execute.
    // KTOR-9955: WinHttp doesn't abort the exchange when rawContent is canceled inside execute.
    @OptIn(InternalAPI::class)
    @Test
    fun `cancelling rawContent stops the underlying stream`() = clientTests(except("Apache5", "CIO", "WinHttp")) {
        test { client ->
            val id = Random.nextInt()

            suspend fun isStreamingActive() =
                client.get("$TEST_SERVER/content/stream/active?id=$id").bodyAsText() == "true"

            client.prepareStream(id = id, delay = 100.milliseconds).execute { response ->
                val body = response.rawContent
                assertEquals(42, body.readInt())
                assertTrue(isStreamingActive())

                body.cancel()

                val streamStopped = withTimeoutOrNull(2.seconds) {
                    while (isStreamingActive()) delay(100.milliseconds)
                } != null
                assertTrue(
                    streamStopped,
                    "Stream $id remained active after rawContent.cancel: " +
                        "bodyClosed=${body.isClosedForRead}, " +
                        "jobCancelled=${response.coroutineContext.job.isCancelled}, " +
                        "jobCompleted=${response.coroutineContext.job.isCompleted}"
                )
            }
        }
    }
}

private suspend fun HttpClient.prepareStream(
    startDelay: Duration? = null,
    delay: Duration? = null,
    chunkSize: Int? = null,
    chunkCount: Int? = null,
    id: Int? = null
): HttpStatement = prepareGet("$TEST_SERVER/content/stream") {
    parameter("startDelay", startDelay?.inWholeMilliseconds)
    parameter("delay", delay?.inWholeMilliseconds)
    parameter("chunkSize", chunkSize)
    parameter("chunkCount", chunkCount)
    parameter("id", id)
}
