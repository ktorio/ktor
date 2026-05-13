/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.apache

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.writeByteBuffer
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.apache.http.HttpEntityEnclosingRequest
import org.apache.http.nio.ContentEncoder
import org.apache.http.nio.IOControl
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.text.toByteArray

class RequestProducerTest {

    @OptIn(InternalAPI::class)
    @Test
    fun testHeadersMerge() = runTest {
        val request = ApacheRequestProducer(
            HttpRequestData(
                Url("http://127.0.0.1/"),
                HttpMethod.Post,
                Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Text.Plain)
                    append(HttpHeaders.ContentLength, "1")
                },
                TextContent("{}", ContentType.Application.Json),
                Job(),
                Attributes()
            ),
            ApacheEngineConfig(),
            EmptyCoroutineContext
        ).generateRequest() as HttpEntityEnclosingRequest

        assertEquals(ContentType.Application.Json.toString(), request.entity.contentType.value)
        assertEquals(2, request.entity.contentLength)
    }

    @Test
    fun testProducingByteArrayContent() = runTest {
        val bytes = "x".repeat(10000).toByteArray()
        val producer = producer(ByteArrayContent(bytes), coroutineContext)

        val encoder = TestEncoder()
        val ioctrl = TestIOControl()

        val result = async {
            encoder.channel.readRemaining().readText()
        }

        while (!encoder.isCompleted) {
            ioctrl.awaitOutputResumed()
            producer.produceContent(encoder, ioctrl)
        }

        assertEquals("x".repeat(10000), result.await())
        producer.close()
    }

    @Test
    fun testProducingNoContent() = runTest {
        val producer = producer(object : OutgoingContent.NoContent() {}, coroutineContext)

        val encoder = TestEncoder()
        val ioctrl = TestIOControl()

        val result = async {
            encoder.channel.readRemaining().readText()
        }

        while (!encoder.isCompleted) {
            ioctrl.awaitOutputResumed()
            producer.produceContent(encoder, ioctrl)
        }

        assertEquals("", result.await())
        producer.close()
    }

    @Test
    fun testProducingReadChannelContent() = runTest {
        val content = ByteChannel(true)
        val body = object : OutgoingContent.ReadChannelContent() {
            override fun readFrom(): ByteReadChannel = content
        }
        val producer = producer(body, coroutineContext)

        val encoder = TestEncoder()
        val ioctrl = TestIOControl()

        launch {
            content.writeStringUtf8("x")
            delay(10)
            content.writeStringUtf8("x")
            delay(10)
            content.writeStringUtf8("x")
            delay(10)
            content.writeStringUtf8("x")
            delay(10)
            content.writeStringUtf8("x")
            delay(10)
            content.close()
        }

        val result = async {
            encoder.channel.readRemaining().readText()
        }

        while (!encoder.isCompleted) {
            ioctrl.awaitOutputResumed()
            producer.produceContent(encoder, ioctrl)
        }

        assertEquals("xxxxx", result.await())
        producer.close()
    }

    @Test
    fun testProducingWriteChannelContent() = runTest {
        val body = ChannelWriterContent(
            body = {
                writeStringUtf8("x")
                delay(100)
                writeStringUtf8("x")
                delay(100)
                writeStringUtf8("x")
                delay(100)
                writeStringUtf8("x")
                delay(100)
                writeStringUtf8("x")
                delay(100)
            },
            contentType = null
        )
        val producer = producer(body, coroutineContext)

        val encoder = TestEncoder()
        val ioctrl = TestIOControl()

        val result = async {
            encoder.channel.readRemaining().readText()
        }

        launch {
            while (!encoder.isCompleted) {
                ioctrl.awaitOutputResumed()
                producer.produceContent(encoder, ioctrl)
            }
        }

        assertEquals("xxxxx", result.await())
        producer.close()
    }

    @Test
    fun testProducingWriteChannelContentOnScale() = runTest {
        val chunk = ByteArray(4 * 1024) { it.toByte() }
        val chunkCount = 1024
        val sampleSize = chunkCount * chunk.size
        val expected = ByteArray(sampleSize) { (it % chunk.size).toByte() }
        repeat(1000) {
            val body = ChannelWriterContent(
                body = {
                    repeat(chunkCount) {
                        writeFully(chunk)
                    }
                },
                contentType = null
            )
            val producer = producer(body, coroutineContext)

            val encoder = TestEncoder()
            val ioctrl = TestIOControl()

            val result = async {
                val result = ByteArray(sampleSize)
                encoder.channel.readFully(result)
                result
            }

            launch {
                while (!encoder.isCompleted) {
                    ioctrl.awaitOutputResumed()
                    producer.produceContent(encoder, ioctrl)
                }
            }

            assertContentEquals(expected, result.await())
            producer.close()
        }
    }

    @OptIn(InternalAPI::class)
    private fun producer(body: OutgoingContent, context: CoroutineContext) = ApacheRequestProducer(
        requestData = HttpRequestData(
            URLBuilder("https://example.com").build(),
            HttpMethod.Get,
            Headers.Empty,
            body,
            Job(),
            Attributes()
        ),
        config = ApacheEngineConfig(),
        callContext = context
    )

    @Test
    fun expectToThrowIllegalStateExceptionIfHostCannotBeExtractedFromRequestURL() {
        val request = HttpRequestBuilder { takeFrom("file://") }.build()
        val cause = assertFailsWith<IllegalArgumentException> {
            ApacheRequestProducer(request, ApacheEngineConfig(), EmptyCoroutineContext)
        }
        assertEquals("Cannot extract host from URL file:///", cause.message)
    }
}

private class TestEncoder : ContentEncoder {
    val channel = ByteChannel()

    @OptIn(InternalAPI::class)
    override fun write(src: ByteBuffer): Int {
        val remaining = src.remaining()
        channel.writeBuffer.writeByteBuffer(src)
        channel.flushWriteBuffer()
        return remaining
    }

    override fun complete() {
        channel.close()
    }

    override fun isCompleted(): Boolean = channel.isClosedForWrite
}

private class TestIOControl : IOControl {
    private val outputResumedChannel = Channel<Unit>(Channel.UNLIMITED)

    @Volatile
    var inputSuspended = false
        private set

    @Volatile
    var outputSuspended = false
        private set

    override fun requestInput() {
        inputSuspended = false
    }

    override fun suspendInput() {
        inputSuspended = true
    }

    override fun requestOutput() {
        outputSuspended = false
        outputResumedChannel.trySend(Unit)
    }

    override fun suspendOutput() {
        outputSuspended = true
    }

    override fun shutdown() {
    }

    suspend fun awaitOutputResumed() {
        if (!outputSuspended) return
        outputResumedChannel.receive()
    }
}
