/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.netty

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.HttpHeaders
import io.netty.util.AttributeKey
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

@ChannelHandler.Sharable
internal class NettyHttpHandler(
    private val callContext: CoroutineContext,
    private val requestTime: GMTDate = GMTDate()
) : SimpleChannelInboundHandler<HttpObject>() {

    private val responseDeferred = CompletableDeferred<HttpResponseData>()
    private val responseChannel = ByteChannel()
    private val responseJob = Job(callContext[Job])

    private lateinit var responseInfo: HttpResponse
    private val contentChunks = Channel<ByteBuf>(Channel.UNLIMITED)
    private val closed = atomic(false)

    init {
        CoroutineScope(callContext + responseJob).launch {
            try {
                contentChunks.consumeEach { chunk ->
                    try {
                        val buffer = ByteArray(chunk.readableBytes())
                        chunk.readBytes(buffer)
                        responseChannel.writeFully(buffer)
                        responseChannel.flush()
                    } finally {
                        chunk.release()
                    }
                }
            } catch (_: ClosedReceiveChannelException) {
            } finally {
                responseChannel.close()
            }
        }
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
        when (msg) {
            is HttpResponse -> {
                responseInfo = msg
                val status = HttpStatusCode.fromValue(msg.status().code())
                val headers = HeadersImpl(msg.headers().toMap())

                // Detect HTTP version from the response
                val protocolVersion = when (msg.protocolVersion().text()) {
                    "HTTP/2.0", "HTTP/2" -> HttpProtocolVersion.HTTP_2_0
                    "HTTP/1.1" -> HttpProtocolVersion.HTTP_1_1
                    "HTTP/1.0" -> HttpProtocolVersion.HTTP_1_0
                    else -> HttpProtocolVersion.HTTP_1_1
                }

                val httpResponseData = HttpResponseData(
                    status,
                    requestTime,
                    headers,
                    protocolVersion,
                    responseChannel,
                    callContext
                )

                responseDeferred.complete(httpResponseData)
            }

            is HttpContent -> {
                if (msg.content().readableBytes() > 0) {
                    contentChunks.trySend(msg.content().retain())
                }

                if (msg is LastHttpContent) {
                    contentChunks.close()
                    responseJob.complete()
                }
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (!closed.compareAndSet(expect = false, update = true)) {
            return
        }

        contentChunks.close(cause)
        responseChannel.close(cause)
        responseJob.completeExceptionally(cause)

        if (!responseDeferred.isCompleted) {
            responseDeferred.completeExceptionally(cause)
        }

        ctx.close()
    }

    suspend fun awaitResponse(): HttpResponseData {
        return responseDeferred.await()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        if (!closed.compareAndSet(expect = false, update = true)) {
            return
        }

        contentChunks.close()
        responseJob.complete()
    }

    companion object {
        private val ATTRIBUTE_KEY: AttributeKey<NettyHttpHandler> =
            AttributeKey.valueOf("NettyHttpHandler")

        fun get(channel: io.netty.channel.Channel): NettyHttpHandler? {
            return channel.attr(ATTRIBUTE_KEY).get()
        }

        fun set(channel: io.netty.channel.Channel, handler: NettyHttpHandler) {
            channel.attr(ATTRIBUTE_KEY).set(handler)
        }
    }
}

private fun HttpHeaders.toMap(): Map<String, List<String>> {
    val result = mutableMapOf<String, MutableList<String>>()
    forEach { entry ->
        result.getOrPut(entry.key) { mutableListOf() }.add(entry.value)
    }
    return result
}
