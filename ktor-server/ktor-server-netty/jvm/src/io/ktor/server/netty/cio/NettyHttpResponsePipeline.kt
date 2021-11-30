/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty.cio

import io.ktor.http.*
import io.ktor.server.netty.*
import io.ktor.server.netty.http1.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import java.io.*
import kotlin.coroutines.*

private const val UNFLUSHED_LIMIT = 65536

/**
 * Contains methods for handling http request with Netty
 * @param context
 * @param coroutineContext
 * @param activeRequests
 * @param isCurrentRequestFullyRead
 * @param isChannelReadCompleted
 */
@OptIn(InternalAPI::class)
internal class NettyHttpResponsePipeline constructor(
    private val context: ChannelHandlerContext,
    private val httpHandler: NettyHttp1Handler,
    override val coroutineContext: CoroutineContext
) : CoroutineScope {
    /**
     * True if there is unflushed written data in channel
     */
    private val isDataNotFlushed: AtomicBoolean = atomic(false)

    /**
     * Represents promise which is marked as success when the last read request is handled.
     * Marked as fail when last read request is failed.
     * Default value is success on purpose to start first request handle
     */
    private var previousCallHandled: ChannelPromise = context.newPromise().also {
        it.setSuccess()
    }

    /**
     * Flush if there is some unflushed data, nothing to read from channel and no active requests
     */
    internal fun flushIfNeeded() {
        if (
            isDataNotFlushed.value &&
            httpHandler.isChannelReadCompleted.value &&
            httpHandler.activeRequests.value == 0L
        ) {
            context.flush()
            isDataNotFlushed.compareAndSet(expect = true, update = false)
        }
    }

    internal fun processResponse(call: NettyApplicationCall) {
        call.previousCallFinished = previousCallHandled
        call.callFinished = context.newPromise()
        previousCallHandled = call.callFinished

        processElement(call)
    }

    private fun processElement(call: NettyApplicationCall) = processOrderedCall(call) {
        try {
            processCall(call)
        } catch (actualException: Throwable) {
            processCallFailed(call, actualException)
        } finally {
            call.responseWriteJob.cancel()
        }
    }

    /**
     * Process [call] with [block] when the response is ready and previous call is successfully processed.
     * [call] won't be processed with [block] if previous call is failed.
     */
    private fun processOrderedCall(call: NettyApplicationCall, block: () -> Unit) {
        call.response.responseReady.addListener responseFlag@{ responseFlagResult ->
            call.previousCallFinished.addListener waitPreviousCall@{ previousCallResult ->
                if (!previousCallResult.isSuccess) {
                    processCallFailed(call, previousCallResult.cause())
                    return@waitPreviousCall
                }
                if (!responseFlagResult.isSuccess) {
                    processCallFailed(call, responseFlagResult.cause())
                    return@waitPreviousCall
                }
                block.invoke()
            }
        }
    }

    private fun processCallFailed(call: NettyApplicationCall, actualException: Throwable) {
        val t = when {
            actualException is IOException && actualException !is ChannelIOException ->
                ChannelWriteException(exception = actualException)
            else -> actualException
        }

        call.response.responseChannel.cancel(t)
        call.responseWriteJob.cancel()
        call.response.cancel()
        call.dispose()
        call.callFinished.setFailure(t)
    }

    private fun processUpgradeResponse(call: NettyApplicationCall, responseMessage: Any): ChannelFuture {
        val future = context.write(responseMessage)
        call.upgrade(context)
        call.isByteBufferContent = true

        context.flush()
        isDataNotFlushed.compareAndSet(expect = true, update = false)
        return future
    }

    private fun finishCall(
        call: NettyApplicationCall,
        lastMessage: Any?,
        lastFuture: ChannelFuture
    ) {
        val prepareForClose =
            (!call.request.keepAlive ||
                call.response.isUpgradeResponse()) &&
                call.isContextCloseRequired()

        val lastMessageFuture = if (lastMessage != null) {
            val future = context.write(lastMessage)
            isDataNotFlushed.compareAndSet(expect = false, update = true)
            future
        } else {
            null
        }

        httpHandler.activeRequests.decrementAndGet()
        call.callFinished.setSuccess()

        lastMessageFuture?.addListener {
            if (prepareForClose) {
                close(lastFuture)
                return@addListener
            }
        }
        if (prepareForClose) {
            close(lastFuture)
            return
        }
        scheduleFlush()
    }

    fun close(lastFuture: ChannelFuture) {
        context.flush()
        isDataNotFlushed.compareAndSet(expect = true, update = false)
        lastFuture.addListener {
            context.close()
        }
    }

    private fun scheduleFlush() {
        context.executor().execute {
            flushIfNeeded()
        }
    }

    private fun processCall(call: NettyApplicationCall) {
        val responseMessage = call.response.responseMessage
        val response = call.response

        val requestMessageFuture = if (response.isUpgradeResponse()) {
            processUpgradeResponse(call, responseMessage)
        } else {
            processResponseHeaders(responseMessage)
        }

        if (responseMessage is FullHttpResponse) {
            return finishCall(call, null, requestMessageFuture)
        } else if (responseMessage is Http2HeadersFrame && responseMessage.isEndStream) {
            return finishCall(call, null, requestMessageFuture)
        }

        val responseChannel = response.responseChannel
        val bodySize = when {
            responseChannel === ByteReadChannel.Empty -> 0
            responseMessage is HttpResponse -> responseMessage.headers().getInt("Content-Length", -1)
            responseMessage is Http2HeadersFrame -> responseMessage.headers().getInt("content-length", -1)
            else -> -1
        }

        launch(context.executor().asCoroutineDispatcher(), start = CoroutineStart.UNDISPATCHED) {
            processResponseBody(
                call,
                response,
                bodySize,
                requestMessageFuture
            )
        }
    }

    private fun processResponseHeaders(responseMessage: Any): ChannelFuture {
        return if (isHeaderFlushNeeded()) {
            val future = context.writeAndFlush(responseMessage)
            isDataNotFlushed.compareAndSet(expect = true, update = false)
            future
        } else {
            val future = context.write(responseMessage)
            isDataNotFlushed.compareAndSet(expect = false, update = true)
            future
        }
    }

    /**
     * True if client is waiting for response header, false otherwise
     */
    private fun isHeaderFlushNeeded(): Boolean {
        val activeRequestsValue = httpHandler.activeRequests.value
        return httpHandler.isChannelReadCompleted.value &&
            !httpHandler.isCurrentRequestFullyRead.value &&
            activeRequestsValue == 1L
    }


    private suspend fun processResponseBody(
        call: NettyApplicationCall,
        response: NettyApplicationResponse,
        bodySize: Int,
        requestMessageFuture: ChannelFuture
    ) {
        try {
            when (bodySize) {
                0 -> processEmptyBody(call, requestMessageFuture)
                in 1..65536 -> processSmallBody(call, response, bodySize)
                -1 -> processBodyWithFlushOnLimitOrEmptyChannel(call, response, requestMessageFuture)
                else -> processBodyWithFlushOnLimit(call, response, requestMessageFuture)
            }
        } catch (actualException: Throwable) {
            processCallFailed(call, actualException)
        }
    }

    private fun processEmptyBody(call: NettyApplicationCall, lastFuture: ChannelFuture) {
        return finishCall(call, call.endOfStream(false), lastFuture)
    }

    private suspend fun processSmallBody(call: NettyApplicationCall, response: NettyApplicationResponse, size: Int) {
        val buffer = context.alloc().buffer(size)
        val channel = response.responseChannel
        val start = buffer.writerIndex()

        channel.readFully(buffer.nioBuffer(start, buffer.writableBytes()))
        buffer.writerIndex(start + size)

        val future = context.write(call.transform(buffer, true))
        isDataNotFlushed.compareAndSet(expect = false, update = true)

        val lastMessage = response.trailerMessage() ?: call.endOfStream(true)

        finishCall(call, lastMessage, future)
    }

    /**
     * Process body with flushing only when limit of written bytes is reached
     */
    private suspend fun processBodyWithFlushOnLimit(
        call: NettyApplicationCall,
        response: NettyApplicationResponse,
        requestMessageFuture: ChannelFuture
    ) = processBigBody(call, response, requestMessageFuture) { _, unflushedBytes ->
        unflushedBytes >= UNFLUSHED_LIMIT
    }

    /**
     * Process body with flushing when there is nothing to read from the response channel
     * or limit of written bytes is reached
     */
    private suspend fun processBodyWithFlushOnLimitOrEmptyChannel(
        call: NettyApplicationCall,
        response: NettyApplicationResponse,
        requestMessageFuture: ChannelFuture
    ) = processBigBody(call, response, requestMessageFuture) { channel, unflushedBytes ->
        unflushedBytes >= UNFLUSHED_LIMIT || channel.availableForRead == 0
    }

    private suspend fun processBigBody(
        call: NettyApplicationCall,
        response: NettyApplicationResponse,
        requestMessageFuture: ChannelFuture,
        shouldFlush: (channel: ByteReadChannel, unflushedBytes: Int) -> Boolean
    ) {
        val channel = response.responseChannel

        var unflushedBytes = 0
        var lastFuture: ChannelFuture = requestMessageFuture

        @Suppress("DEPRECATION")
        channel.lookAheadSuspend {
            while (true) {
                val buffer = request(0, 1)
                if (buffer == null) {
                    if (!awaitAtLeast(1)) break
                    continue
                }

                val rc = buffer.remaining()
                val buf = context.alloc().buffer(rc)
                val idx = buf.writerIndex()
                buf.setBytes(idx, buffer)
                buf.writerIndex(idx + rc)

                consumed(rc)
                unflushedBytes += rc

                val message = call.transform(buf, false)

                if (shouldFlush.invoke(channel, unflushedBytes)) {
                    context.read()
                    val future = context.writeAndFlush(message)
                    isDataNotFlushed.compareAndSet(expect = true, update = false)
                    lastFuture = future
                    future.suspendAwait()
                    unflushedBytes = 0
                } else {
                    lastFuture = context.write(message)
                    isDataNotFlushed.compareAndSet(expect = false, update = true)
                }
            }
        }

        val lastMessage = response.trailerMessage() ?: call.endOfStream(false)
        finishCall(call, lastMessage, lastFuture)
    }
}

@OptIn(InternalAPI::class)
private fun NettyApplicationResponse.isUpgradeResponse() =
    status()?.value == HttpStatusCode.SwitchingProtocols.value

public class NettyResponsePipelineException(message: String) : Exception(message)
