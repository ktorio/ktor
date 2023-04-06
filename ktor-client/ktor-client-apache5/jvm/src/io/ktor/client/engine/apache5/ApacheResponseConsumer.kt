/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache5

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import org.apache.hc.client5.http.async.methods.*
import org.apache.hc.core5.concurrent.*
import org.apache.hc.core5.function.*
import org.apache.hc.core5.http.*
import org.apache.hc.core5.http.HttpResponse
import org.apache.hc.core5.http.nio.*
import org.apache.hc.core5.http.nio.entity.*
import org.apache.hc.core5.http.protocol.*
import org.apache.hc.core5.util.*
import java.io.*
import java.nio.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*

private object CloseChannel

internal class BasicResponseConsumer(private val dataConsumer: ApacheResponseConsumer) :
    AsyncResponseConsumer<Unit> {

    internal val responseDeferred = CompletableDeferred<HttpResponse>()

    override fun consumeResponse(
        response: HttpResponse,
        entityDetails: EntityDetails?,
        httpContext: HttpContext,
        resultCallback: FutureCallback<Unit>
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

@OptIn(InternalCoroutinesApi::class)
internal class ApacheResponseConsumer(
    parentContext: CoroutineContext,
    private val requestData: HttpRequestData
) : AsyncEntityConsumer<Unit>, CoroutineScope {

    private val consumerJob = Job(parentContext[Job])
    override val coroutineContext: CoroutineContext = parentContext + consumerJob

    private val channel = ByteChannel().also {
        it.attachJob(consumerJob)
    }

    @Volatile
    private var capacityChannel: CapacityChannel? = null

    private val messagesQueue = Channel<Any>(capacity = UNLIMITED)

    internal val responseChannel: ByteReadChannel = channel
    private val capacity = atomic(channel.availableForWrite)

    init {
        coroutineContext[Job]?.invokeOnCompletion(onCancelling = true) { cause ->
            if (cause != null) {
                responseChannel.cancel(cause)
            }
        }

        launch(coroutineContext) {
            for (message in messagesQueue) {
                when (message) {
                    is CloseChannel -> close()

                    is ByteBuffer -> {
                        val written = message.remaining()
                        channel.writeFully(message)
                        when (capacityChannel) {
                            null -> capacity.addAndGet(written)
                            else -> capacityChannel!!.update(written)
                        }
                    }

                    else -> throw IllegalStateException("Unknown message $message")
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
        if (channel.isClosedForWrite2) {
            channel.closedCause2?.let { throw it }
        }
        messagesQueue.trySend(src.copy())
    }

    override fun streamEnd(trailers: List<Header>?) {
        messagesQueue.trySend(CloseChannel)
    }

    override fun streamStart(entityDetails: EntityDetails, resultCallback: FutureCallback<Unit>) {}

    override fun failed(cause: Exception) {
        val mappedCause = mapCause(cause, requestData)
        consumerJob.completeExceptionally(mappedCause)
        responseChannel.cancel(mappedCause)
    }

    internal fun close() {
        channel.close()
        consumerJob.complete()
    }

    override fun getContent() = Unit
}
