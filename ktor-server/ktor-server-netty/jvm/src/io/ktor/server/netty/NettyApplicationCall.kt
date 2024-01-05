/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.netty.buffer.*
import io.netty.channel.*
import io.netty.util.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*

public abstract class NettyApplicationCall(
    application: Application,
    public val context: ChannelHandlerContext,
    private val requestMessage: Any,
) : BaseApplicationCall(application) {

    public abstract override val request: NettyApplicationRequest
    public abstract override val response: NettyApplicationResponse

    internal lateinit var previousCallFinished: ChannelPromise

    /**
     * Set success when the response is ready to read or failed if a response is cancelled
     */
    internal lateinit var finishedEvent: ChannelPromise

    public val responseWriteJob: Job = Job()

    private val messageReleased = atomic(false)

    internal var isByteBufferContent = false

    /**
     * Returns http content object with [buf] content if [isByteBufferContent] is false,
     * [buf] otherwise.
     */
    internal open fun prepareMessage(buf: ByteBuf, isLastContent: Boolean): Any {
        return buf
    }

    /**
     * Returns the 'end of content' http marker if [isByteBufferContent] is false,
     * null otherwise
     */
    internal open fun prepareEndOfStreamMessage(lastTransformed: Boolean): Any? {
        return null
    }

    /**
     * Add [MessageToByteEncoder] to the channel handler pipeline if http upgrade is supported,
     * [IllegalStateException] otherwise
     */
    internal open fun upgrade(dst: ChannelHandlerContext) {
        throw IllegalStateException("Already upgraded")
    }

    internal abstract fun isContextCloseRequired(): Boolean

    internal suspend fun finish() {
        try {
            response.ensureResponseSent()
        } catch (cause: Throwable) {
            finishedEvent.setFailure(cause)
            finishComplete()
            throw cause
        }

        if (responseWriteJob.isCompleted) {
            finishComplete()
            return
        }

        return finishSuspend()
    }

    private suspend fun finishSuspend() {
        try {
            responseWriteJob.join()
        } finally {
            finishComplete()
        }
    }

    private fun finishComplete() {
        responseWriteJob.cancel()
        request.close()
        releaseRequestMessage()
    }

    internal fun dispose() {
        response.close()
        request.close()
        releaseRequestMessage()
    }

    private fun releaseRequestMessage() {
        if (messageReleased.compareAndSet(expect = false, update = true)) {
            ReferenceCountUtil.release(requestMessage)
        }
    }
}
