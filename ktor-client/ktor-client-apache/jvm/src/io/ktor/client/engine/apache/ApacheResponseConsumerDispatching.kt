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
    private val queue = ArrayBlockingQueue<Runnable>(10)

    private val interestController = atomic<IOControl?>(null)

    override val coroutineContext: CoroutineContext = callContext + Dispatcher()

    private val out = ByteChannel(true)

    private var decoderWaiter: CancellableContinuation<ContentDecoder?>? = null

    private val job = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            var rc = 0
            do {
                val decoder = waitForDecoder() ?: break

                do {
                    out.write { dst ->
                        rc = decoder.read(dst)
                    }
                } while (rc > 0)
            } while (rc >= 0)
        } catch (cause: Throwable) {
            out.close(cause)
        } finally {
            out.close()
        }
    }

    private suspend fun waitForDecoder(): ContentDecoder? = suspendCancellableCoroutine {
        decoderWaiter = it
    }

    override fun consumeContent(decoder: ContentDecoder, ioctrl: IOControl) {
        do {
            processLoop(Result.success(decoder))

            when {
                decoderWaiter != null -> {
                }
                job.isActive -> {
                    suspendInput(ioctrl)

                    if (queue.isNotEmpty()) {
                        resumeInput()
                    } else {
                        return
                    }
                }
                else -> {
                    if (!decoder.isCompleted) {
                        decoder.discardAll()
                    }
                }
            }
        } while (queue.isNotEmpty())
    }

    override fun failed(ex: Exception) {
        job.cancel(CancellationException("Failed to execute request", ex))
        processLoop(Result.failure(ex))
    }

    override fun cancel(): Boolean {
        job.cancel()
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
        if (job.isActive) {
            block(response, out)
        }
    }

    private fun processLoop(result: Result<ContentDecoder?>) {
        decoderWaiter?.let { continuation ->
            this.decoderWaiter = null
            continuation.resumeWith(result)
        }

        while (true) {
            queue.poll()?.run() ?: break
        }
    }

    private fun ContentDecoder.discardAll() {
        val buffer = ByteBuffer.allocate(8192)
        do {
            buffer.clear()
            if (read(buffer) <= 0) break
        } while (true)
    }

    private fun suspendInput(ioctrl: IOControl) {
        ioctrl.suspendInput()
        interestController.value = ioctrl
    }

    private fun resumeInput() {
        interestController.getAndUpdate { before ->
            if (before == null) return
            null
        }?.let { ioctrl ->
            ioctrl.requestInput()
        }
    }

    private inner class Dispatcher : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.add(block)
            resumeInput()
        }
    }
}
