/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.server.application.*
import io.ktor.server.engine.*
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

    /**
     * Tracks the lifetime of the response write on the Netty I/O thread.
     *
     * This Job is a child of the call's coroutine [Job] (see [coroutineContext]), so the call's
     * coroutine remains "completing" — and is awaited by the parent application Job during graceful
     * shutdown — until the response is fully written. This removes the need to suspend on
     * [Job.join] from the application thread at the end of each call (for example, the
     * [io.ktor.server.netty.NettyApplicationEngine] AFTER_CALL_PHASE interceptor).
     *
     * Initialized via [initResponseWriteJob] on the Netty I/O thread synchronously after call
     * construction (from `processResponse`) and before the user handler coroutine is launched.
     * The deferred initialization is required because subclasses bind [coroutineContext] in their
     * own primary constructor, after the base class constructor has finished.
     */
    public lateinit var responseWriteJob: Job
        private set

    /**
     * Initializes [responseWriteJob] as a child of the call's coroutine [Job]. Called synchronously
     * on the Netty I/O thread right after the call is constructed and before the user handler
     * coroutine is launched, so the field is safely published to all subsequent readers via the
     * handler-dispatch happens-before edge.
     */
    internal fun initResponseWriteJob() {
        val callJob = coroutineContext[Job]
        val job = Job(parent = callJob)
        job.invokeOnCompletion { onResponseWriteCompleted() }
        responseWriteJob = job
    }

    private val messageReleased = atomic(false)

    internal var isByteBufferContent = false
    internal var isStreamingResponse = false

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

    /**
     * Marks the call as ready to finish, without suspending the calling coroutine.
     *
     * The response writer runs on the Netty I/O thread and signals completion through
     * [responseWriteJob]; because that job is a child of the call's coroutine [Job], the call
     * naturally remains "completing" until the write finishes — there is no need to join here.
     * Per-call cleanup (request close + request message release) is performed by
     * [onResponseWriteCompleted] when [responseWriteJob] completes (registered as an
     * `invokeOnCompletion` handler in [initResponseWriteJob]).
     *
     * Throws if [NettyApplicationResponse.ensureResponseSent] fails. In that case the failure is
     * propagated through [finishedEvent] and the response write job is cancelled so cleanup still
     * runs via the registered completion handler.
     */
    internal fun finish() {
        try {
            response.ensureResponseSent()
        } catch (cause: Throwable) {
            finishedEvent.setFailure(cause)
            // Cancelling the job drives `onResponseWriteCompleted` via invokeOnCompletion.
            responseWriteJob.cancel()
            throw cause
        }
    }

    private fun onResponseWriteCompleted() {
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
