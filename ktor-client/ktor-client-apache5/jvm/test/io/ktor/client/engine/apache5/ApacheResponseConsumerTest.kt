/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.impl.BasicEntityDetails
import org.apache.hc.core5.http.message.BasicHttpResponse
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class ApacheResponseConsumerTest {

    @Test
    fun `FutureCallback completed is called when response with body is fully consumed`() = runTest(
        timeout = 10.seconds
    ) {
        val callbackResult = CompletableDeferred<Unit>()
        val callback = trackingCallback(onCompleted = { callbackResult.complete(Unit) })

        val consumerContext = Dispatchers.Default + Job()
        val bodyConsumer = ApacheResponseConsumer(consumerContext, requestData)
        val responseConsumer = BasicResponseConsumer(bodyConsumer)

        responseConsumer.consumeResponse(
            BasicHttpResponse(200),
            BasicEntityDetails(5, ContentType.TEXT_PLAIN),
            null,
            callback,
        )

        responseConsumer.consume(ByteBuffer.wrap("hello".toByteArray()))
        responseConsumer.streamEnd(null)

        callbackResult.await()
    }

    @Test
    fun `FutureCallback completed is called for response without body`() = runTest(timeout = 10.seconds) {
        val callbackResult = CompletableDeferred<Unit>()
        val callback = trackingCallback(onCompleted = { callbackResult.complete(Unit) })

        val consumerContext = Dispatchers.Default + Job()
        val bodyConsumer = ApacheResponseConsumer(consumerContext, requestData)
        val responseConsumer = BasicResponseConsumer(bodyConsumer)

        responseConsumer.consumeResponse(BasicHttpResponse(204), null, null, callback)

        callbackResult.await()
    }

    @Test
    fun `FutureCallback failed is called on error`() = runTest(timeout = 10.seconds) {
        val failureException = CompletableDeferred<Exception>()
        val callback = trackingCallback(onFailed = { failureException.complete(it) })

        val consumerContext = Dispatchers.Default + Job()
        val bodyConsumer = ApacheResponseConsumer(consumerContext, requestData)
        val responseConsumer = BasicResponseConsumer(bodyConsumer)

        responseConsumer.consumeResponse(
            BasicHttpResponse(200),
            BasicEntityDetails(5, ContentType.TEXT_PLAIN),
            null,
            callback,
        )

        val cause = IOException("connection reset")
        responseConsumer.failed(cause)

        val received = failureException.await()
        assertNotNull(received)
        assertEquals("connection reset", received.message)
    }

    @Test
    fun `FutureCallback is called at most once when both streamEnd and failed fire`() = runTest(timeout = 10.seconds) {
        val terminalCallCount = AtomicInteger(0)
        val firstTerminal = CompletableDeferred<Unit>()
        val callback = object : FutureCallback<Unit> {
            override fun completed(result: Unit) {
                terminalCallCount.incrementAndGet()
                firstTerminal.complete(Unit)
            }

            override fun failed(ex: Exception) {
                terminalCallCount.incrementAndGet()
                firstTerminal.complete(Unit)
            }

            override fun cancelled() = Unit
        }

        val parentJob = Job()
        val consumerContext = Dispatchers.Default + parentJob
        val bodyConsumer = ApacheResponseConsumer(consumerContext, requestData)
        val responseConsumer = BasicResponseConsumer(bodyConsumer)

        responseConsumer.consumeResponse(
            BasicHttpResponse(200),
            BasicEntityDetails(5, ContentType.TEXT_PLAIN),
            null,
            callback,
        )

        responseConsumer.streamEnd(null) // enqueues CloseChannel → close() runs asynchronously
        responseConsumer.failed(IOException("network error")) // may also invoke the callback

        // Close the queue so the message-queue coroutine finishes after processing CloseChannel,
        // then join consumerJob (child of parentJob) which waits for all its children too.
        responseConsumer.releaseResources()
        parentJob.children.forEach { it.join() }

        assertEquals(1, terminalCallCount.get(), "FutureCallback must be invoked exactly once")
    }

    private fun trackingCallback(
        onCompleted: () -> Unit = {},
        onFailed: (Exception) -> Unit = {},
    ) = object : FutureCallback<Unit> {
        override fun completed(result: Unit) = onCompleted()
        override fun failed(ex: Exception) = onFailed(ex)
        override fun cancelled() = Unit
    }

    @OptIn(InternalAPI::class)
    private val requestData = HttpRequestData(
        Url("http://localhost"),
        HttpMethod.Get,
        Headers.Empty,
        TextContent("", io.ktor.http.ContentType.Text.Any),
        Job(),
        Attributes(),
    )
}
