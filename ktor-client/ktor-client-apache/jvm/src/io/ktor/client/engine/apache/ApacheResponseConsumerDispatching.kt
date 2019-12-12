/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import org.apache.http.*
import org.apache.http.nio.*
import org.apache.http.nio.protocol.*
import org.apache.http.protocol.*
import java.nio.*
import java.util.concurrent.*
import kotlin.coroutines.*

internal class ApacheResponseConsumerDispatching(
    callContext: CoroutineContext,
    private val block: (HttpResponse, ByteReadChannel) -> Unit
) : HttpAsyncResponseConsumer<Unit>, CoroutineScope {
    private val interestController = InterestControllerHolder()

    private val dispatcher = ReactorLoopDispatcher()

    override val coroutineContext: CoroutineContext = callContext + dispatcher

    private var decoderWaiter: CancellableContinuation<ContentDecoder?>? = null

    /**
     * This coroutine is executed with our custom [ReactorLoopDispatcher] so it will only run on apache's reactor thread
     * inside of callback's invocations (such as [consumeContent] or [failed]).
     */
    private val job = writer {
        try {
            var rc = 0
            do {
                val decoder = waitForDecoder() ?: break

                do {
                    channel.write { dst ->
                        rc = decoder.read(dst)
                    }
                } while (rc > 0)
            } while (rc >= 0)
        } catch (cause: Throwable) {
            channel.close(cause)
        } finally {
            channel.close()
        }
    }

    private val contentChannel: ByteReadChannel = job.channel

    private suspend fun waitForDecoder(): ContentDecoder? = suspendCancellableCoroutine {
        decoderWaiter = it
    }

    init {
        // the coroutine is dispatched but not yet started
        // (since we control it's execution using the custom dispatcher)
        // so we start it's execution here so it should suspend at decoder suspension
        processLoop(Result.failure(IllegalStateException("The coroutine shouldn't be suspended at this point yet.")))

        check(decoderWaiter != null) { "Writer coroutine should suspend until decoder available." }
    }

    override fun consumeContent(decoder: ContentDecoder, ioctrl: IOControl) {
        do {
            interestController.resumeInputIfPossible()
            processLoop(Result.success(decoder))

            when {
                decoderWaiter != null -> {
                    // if the coroutine is waiting for decoder
                    // then we don't touch interest
                    // let's wait for the next consumeContent invocation
                }
                job.isActive -> {
                    // the coroutine is running somewhere
                    interestController.suspendInput(ioctrl)
                    // no double check needed here since we do loop and resume input at the loop beginning
                }
                else -> {
                    // job is cancelled or crashes so we simply discard all incoming bytes reported by Apache
                    if (!decoder.isCompleted) {
                        decoder.discardAll()
                    }
                }
            }
        } while (dispatcher.hasTasks())
    }

    override fun failed(ex: Exception) {
        job.cancel(CancellationException("Failed to execute request", ex))
        processLoop(Result.failure(ex))
    }

    @UseExperimental(InternalCoroutinesApi::class)
    override fun cancel(): Boolean {
        job.cancel()
        processLoop(Result.failure(job.getCancellationException()))
        return true
    }

    override fun close() {
    }

    @UseExperimental(InternalCoroutinesApi::class)
    override fun getException(): Exception? {
        return job.getCancellationException().cause as? Exception
    }

    override fun getResult() {
    }

    override fun isDone(): Boolean = job.isCompleted

    override fun responseCompleted(context: HttpContext) {
        processLoop(Result.success(null))
    }

    override fun responseReceived(response: HttpResponse) {
        block(response, contentChannel)
    }

    private fun processLoop(result: Result<ContentDecoder?>) {
        decoderWaiter?.let { continuation ->
            this.decoderWaiter = null
            continuation.resumeWith(result)
        }

        dispatcher.processLoop()
    }

    private fun ContentDecoder.discardAll() {
        val buffer = ByteBuffer.allocate(8192)
        do {
            buffer.clear()
            if (read(buffer) <= 0) break
        } while (true)
    }

    /**
     * Holder class to guard reference to [IOControl] so one couldn't access it improperly.
     */
    private class InterestControllerHolder {
        /**
         * Contains [IOControl] only when it is suspended. One should steal it first before requesting input again.
         */
        private val interestController = atomic<IOControl?>(null)

        fun suspendInput(ioControl: IOControl) {
            ioControl.suspendInput()
            interestController.update { before ->
                check(before == null || before === ioControl) { "IOControl is already published" }
                ioControl
            }
        }

        fun resumeInputIfPossible() {
            interestController.getAndUpdate { before ->
                if (before == null) return
                null
            }?.requestInput()
        }
    }

    private inner class ReactorLoopDispatcher : CoroutineDispatcher() {
        private val queue = ArrayBlockingQueue<Runnable>(2)

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.add(block)
            interestController.resumeInputIfPossible()
        }

        fun processLoop() {
            while (true) {
                queue.poll()?.run() ?: break
            }
        }

        fun hasTasks(): Boolean = queue.isNotEmpty()
    }
}
