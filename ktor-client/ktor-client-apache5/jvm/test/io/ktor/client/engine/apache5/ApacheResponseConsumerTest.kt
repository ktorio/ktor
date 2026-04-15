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
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.impl.BasicEntityDetails
import org.apache.hc.core5.http.message.BasicHttpResponse
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ApacheResponseConsumerTest {

    @Test
    fun `FutureCallback completed is called when response with body is fully consumed`() = runBlocking {
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

        withTimeout(5.seconds) { callbackResult.await() }
        assertTrue(callbackResult.isCompleted)
    }

    @Test
    fun `FutureCallback completed is called for response without body`() = runBlocking {
        val callbackResult = CompletableDeferred<Unit>()
        val callback = trackingCallback(onCompleted = { callbackResult.complete(Unit) })

        val consumerContext = Dispatchers.Default + Job()
        val bodyConsumer = ApacheResponseConsumer(consumerContext, requestData)
        val responseConsumer = BasicResponseConsumer(bodyConsumer)

        responseConsumer.consumeResponse(BasicHttpResponse(204), null, null, callback)

        withTimeout(5.seconds) { callbackResult.await() }
        assertTrue(callbackResult.isCompleted)
    }

    @Test
    fun `FutureCallback failed is called on error`() = runBlocking {
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

        val received = withTimeout(5.seconds) { failureException.await() }
        assertNotNull(received)
        assertEquals("connection reset", received.message)
    }

    private fun trackingCallback(
        onCompleted: () -> Unit = {},
        onFailed: (Exception) -> Unit = {},
    ) = object : FutureCallback<Unit> {
        override fun completed(result: Unit) = onCompleted()
        override fun failed(ex: Exception) = onFailed(ex)
        override fun cancelled() {}
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
