/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import org.apache.hc.core5.concurrent.CallbackContribution
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.EntityDetails
import org.apache.hc.core5.http.Header
import org.apache.hc.core5.http.HttpResponse
import org.apache.hc.core5.http.nio.AsyncEntityConsumer
import org.apache.hc.core5.http.nio.AsyncResponseConsumer
import org.apache.hc.core5.http.nio.CapacityChannel
import org.apache.hc.core5.http.protocol.HttpContext
import java.nio.ByteBuffer
import java.util.concurrent.Future
import kotlin.coroutines.CoroutineContext

private object CloseChannel

internal class BasicResponseConsumer(private val dataConsumer: ApacheResponseConsumer) :
    AsyncResponseConsumer<Unit> {

    internal val responseDeferred = CompletableDeferred<HttpResponse>()

    override fun consumeResponse(
        response: HttpResponse,
        entityDetails: EntityDetails?,
        context: HttpContext?,
        resultCallback: FutureCallback<Unit>,
    ) {
        responseDeferred.complete(response)
        if (entityDetails != null) {
            dataConsumer.streamStart(
                entityDetails,
                object : CallbackContribution<Unit>(resultCallback) {
                    override fun completed(body: Unit) {
                        resultCallback.completed(Unit)
                    }
                }
            )
        } else {
            dataConsumer.close()
            resultCallback.completed(Unit)
        }
    }

    override fun informationResponse(response: HttpResponse, httpContext: HttpContext) {
    }

    override fun updateCapacity(capacityChannel: CapacityChannel) {
        dataConsumer.updateCapacity(capacityChannel)
    }

    override fun consume(src: ByteBuffer) {
        dataConsumer.consume(src)
    }

    override fun streamEnd(trailers: List<Header>?) {
        dataConsumer.streamEnd(trailers)
    }

    override fun failed(cause: Exception) {
        responseDeferred.completeExceptionally(cause)
        dataConsumer.failed(cause)
    }

    override fun releaseResources() {
        dataConsumer.releaseResources()
    }
}

internal class ApacheResponseConsumer(
    parentContext: CoroutineContext,
    private val requestData: HttpRequestData
) : AsyncEntityConsumer<Unit>, CoroutineScope {

    private val closed = atomic(false)
    private val cancellationCause = atomic<Throwable?>(null)

    private val consumerJob = Job(parentContext.job)
    override val coroutineContext: CoroutineContext = parentContext + consumerJob

    private val channel = ByteChannel().apply {
        attachJob(consumerJob)
    }

    @Volatile
    private var capacityChannel: CapacityChannel? = null

    private val streamResultCallback = atomic<FutureCallback<Unit>?>(null)

    private val messagesQueue = Channel<Any>(capacity = UNLIMITED)

    internal val responseChannel: ByteReadChannel = channel
    private val capacity = atomic(channel.availableForWrite)

    init {
        launch(CoroutineName("apache-response-consumer")) {
            for (message in messagesQueue) {
                when (message) {
                    is CloseChannel -> close()

                    is ByteBuffer -> {
                        val written = message.remaining()
                        channel.writeFully(message)
                        channel.flush()
                        when (val channel = capacityChannel) {
                            null -> capacity.addAndGet(written)
                            else -> channel.update(written)
                        }
                    }

                    else -> error("Unknown message $message")
                }
            }
        }
    }

    override fun releaseResources() {
        messagesQueue.close()
    }

    override fun updateCapacity(capacityChannel: CapacityChannel) {
        if (this.capacityChannel == null) {
            this.capacityChannel = capacityChannel
            capacityChannel.update(capacity.value)
        }
    }

    override fun consume(src: ByteBuffer) {
        // Silently discard when the channel is closed (e.g. caller scope was cancelled).
        // Throwing here (even IOException per the interface contract) causes Apache to invoke its
        // error-recovery path mid-body-stream, which either corrupts the connection pool state or
        // triggers a retry on an already-shutdown scheduler (RejectedExecutionException).
        if (channel.isClosedForWrite) return
        messagesQueue.trySend(src.copy())
    }

    override fun streamEnd(trailers: List<Header>?) {
        messagesQueue.trySend(CloseChannel)
    }

    override fun streamStart(entityDetails: EntityDetails, resultCallback: FutureCallback<Unit>) {
        streamResultCallback.value = resultCallback
    }

    override fun failed(cause: Exception) {
        if (!closed.compareAndSet(expect = false, update = true)) return
        if (completeCallbackFromCancellationCause()) return

        val mappedCause = mapCause(cause, requestData)
        consumerJob.completeExceptionally(mappedCause)
        streamResultCallback.getAndSet(null)?.failed(cause)
    }

    internal fun close() {
        if (!closed.compareAndSet(expect = false, update = true)) return
        if (completeCallbackFromCancellationCause()) return

        channel.close()
        consumerJob.complete()
        streamResultCallback.getAndSet(null)?.completed(Unit)
    }

    private fun completeCallbackFromCancellationCause(): Boolean {
        val completionCause = cancellationCause.getAndSet(null) ?: return false
        if (completionCause is CancellationException) {
            streamResultCallback.getAndSet(null)?.cancelled()
        } else {
            streamResultCallback.getAndSet(null)?.failed(
                completionCause as? Exception ?: CancellationException("Response consumer failed", completionCause)
            )
        }
        return true
    }

    override fun getContent() = Unit

    @OptIn(InternalCoroutinesApi::class)
    fun attachFuture(future: Future<Unit>) {
        consumerJob.invokeOnCompletion(onCancelling = true) { cause ->
            // Calling `future.cancel()` triggers `failed` path with `InterruptedIOException`
            if (cause != null && cancellationCause.compareAndSet(null, cause)) future.cancel(true)
        }
    }
}
