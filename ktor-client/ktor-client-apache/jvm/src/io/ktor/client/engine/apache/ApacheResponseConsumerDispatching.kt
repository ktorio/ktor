/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import org.apache.http.*
import org.apache.http.nio.*
import org.apache.http.nio.protocol.*
import org.apache.http.protocol.*
import java.net.*
import java.nio.*
import kotlin.coroutines.*

internal class ApacheResponseConsumerDispatching(
    callContext: CoroutineContext,
    private val requestData: HttpRequestData?,
    private val block: (HttpResponse, ByteReadChannel) -> Unit
) : HttpAsyncResponseConsumer<Unit>, CoroutineScope {
    private val interestController = InterestControllerHolder()
    private val dispatcher = ReactorLoopDispatcher(interestController, 1)

    override val coroutineContext: CoroutineContext = callContext + dispatcher

    // this is not volatile because it is always accessed from the reactor thread except for the constructor
    private var decoderWaiter: CancellableContinuation<ContentDecoder?>? = null

    /**
     * This coroutine is executed with our custom [ReactorLoopDispatcher] so it will only run on apache's reactor thread
     * inside of callback's invocations (such as [consumeContent] or [failed]).
     */
    private val job = writer(CoroutineName("content-decoder")) {
        var bytesRead = 0
        do {
            val decoder = waitForDecoder() ?: break

            do {
                channel.write { dst ->
                    bytesRead = decoder.read(dst)
                }
            } while (bytesRead > 0)
            channel.flush()
        } while (bytesRead != -1 && !decoder.isCompleted)

        // note: decoder.read may return 0 when the decoder is already completed
        // so we need to check both read count and decoder completion
    }

    private val contentChannel: ByteReadChannel = job.channel

    /**
     * We hold job completion exception here instead of calling job.getCancellationException
     */
    private val jobCompletionCause = atomic<Throwable?>(null)

    /**
     * Wait for a [ContentDecoder] ready for decoding (probably empty but unprocessed yet).
     */
    private suspend fun waitForDecoder(): ContentDecoder? = suspendCancellableCoroutine {
        decoderWaiter = it
    }

    init {
        // The coroutine is already started and dispatched but not yet executed
        // (since it may only run inside of [ReactorLoopDispatcher])
        // So we start its execution here, and it should suspend at [waitForDecoder] invocation.
        processLoop(Result.failure(IllegalStateException("The coroutine shouldn't be suspended at this point yet.")))

        check(!coroutineContext.isActive || decoderWaiter != null) {
            "Writer coroutine should suspend until decoder available."
        }

        job.invokeOnCompletion { cause ->
            jobCompletionCause.value = cause
        }

        jobCompletionCause.value?.let {
            // rethrow if the coroutine crashed immediately
            throw it
        }
    }

    override fun consumeContent(decoder: ContentDecoder, ioctrl: IOControl) {
        do {
            // resume if suspended before (this is double-check from a previous loop iteration)
            interestController.resumeInputIfPossible()

            // run dispatchers loop (the coroutine) sending the decoder to it
            processLoop(Result.success(decoder))

            when {
                decoderWaiter != null -> {
                    // If the coroutine is waiting for a decoder again
                    // then we don't touch interest
                    // let's wait for the next consumeContent invocation
                    check(!decoder.isCompleted) { "The coroutine shouldn't wait for decoder while it is completed." }
                }
                job.isActive -> {
                    check(!decoder.isCompleted) { "Decoder shouldn't be completed while the coroutine is on suspension" }
                    // The coroutine is in suspension somewhere else (possibly due to back-pressure).
                    interestController.suspendInput(ioctrl)
                    // no double check needed here since we do loop and resume input at the loop beginning
                }
                else -> {
                    // The coroutine is cancelled or crashed, so we simply discard all incoming bytes provided by Apache
                    if (!decoder.isCompleted) {
                        decoder.discardAll()
                    }
                }
            }

            // We retry the loop if there were tasks enqueued.
            // This can only happen if the coroutine has been resumed
            // from another thread. For example, a channel reader released some space in the channel,
            // thus, we may continue execution.
        } while (dispatcher.hasTasks())
    }

    override fun failed(cause: Exception) {
        val mappedCause = when {
            cause is ConnectException && cause.isTimeoutException() -> ConnectTimeoutException(requestData!!, cause)
            cause is java.net.SocketTimeoutException -> SocketTimeoutException(requestData!!, cause)
            else -> cause
        }

        job.cancel(CancellationException("Failed to execute request", mappedCause))
        processLoop(Result.failure(cause))
    }

    override fun cancel(): Boolean {
        job.cancel()
        processLoop(Result.failure(IllegalStateException("Job cancellation should do resume.")))
        return true
    }

    override fun close() {
    }

    override fun getException(): Exception? {
        return jobCompletionCause.value as? Exception
    }

    override fun getResult() {
    }

    override fun isDone(): Boolean = job.isCompleted

    override fun responseCompleted(context: HttpContext) {
        // Passing null should cause the coroutine to exit normally if not yet.
        processLoop(Result.success(null))

        // We believe that this should never happen during backpressure
        // since this callback is only invoked for an empty response or when the decoder is completed
        // that couldn't be true if we have backpressure suspension
        check(job.isCompleted) { "The coroutine didn't complete in time." }
    }

    override fun responseReceived(response: HttpResponse) {
        block(response, contentChannel)
    }

    /**
     * Process dispatchers event loop resuming decoder waiting with the specified [result].
     */
    private fun processLoop(result: Result<ContentDecoder?>) {
        decoderWaiter?.let { continuation ->
            this.decoderWaiter = null
            continuation.resumeWith(result)
        }

        dispatcher.processLoop()
    }

    /**
     * Discard all ready bytes in [ContentDecoder].
     */
    private fun ContentDecoder.discardAll() {
        val buffer = ByteBuffer.allocate(8192)
        do {
            buffer.clear()
            if (read(buffer) <= 0) break
        } while (true)
    }
}
