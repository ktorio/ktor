/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import org.apache.http.*
import org.apache.http.nio.*
import org.apache.http.nio.protocol.*
import org.apache.http.protocol.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

internal class ApacheResponseConsumer(
    override val coroutineContext: CoroutineContext,
    private val block: (HttpResponse, ByteReadChannel) -> Unit
) : CoroutineScope, HttpAsyncResponseConsumer<Unit> {
    private val continuation = atomic<Continuation<ContentDecoder?>?>(null)

    @Volatile
    private var exception: Exception? = null

    @Volatile
    private lateinit var control: IOControl

    private val suspended = atomic(false)
    private val completed = atomic(false)

    private val content = writer {
        channel.writeSuspendSession {
            var done = false

            while (!done) {
                tryAwait(1)

                inDecoderThread { decoder ->
                    var count = 0
                    while (!decoder.isCompleted) {
                        val buffer = request(1)

                        if (buffer == null) {
                            suspendInput()
                            break
                        } else {
                            requestInput()
                        }

                        buffer.writeDirect(1) {
                            count = decoder.read(it)
                        }

                        if (count < 0) {
                            done = true
                            return@inDecoderThread
                        }

                        if (count == 0) {
                            requestInput()
                            break
                        }

                        written(count)
                        flush()
                    }

                    if (decoder.isCompleted) {
                        done = true
                    }
                }

                done = done || completed.value
                coroutineContext[Job]!!.ensureActive()
            }
        }
    }.let { job ->
        @UseExperimental(InternalCoroutinesApi::class)
        job.invokeOnCompletion(true) { cause ->
            if (cause is Exception) {
                // note: this is important since inDecoderThread suspension is non-cancellable
                exception = cause
                releaseContinuation(Result.failure(cause))
            }
        }

        job.channel
    }


    override fun isDone(): Boolean = !isActive

    override fun responseReceived(response: HttpResponse) {
        block(response, content)
    }

    override fun consumeContent(decoder: ContentDecoder, ioctrl: IOControl) {
        control = ioctrl

        var current = continuation.getAndSet(null)
        if (current == null) {
            suspendInput()
            current = continuation.getAndSet(null)

            if (current == null) {
                return
            }

            requestInput()
        }

        current.resume(decoder)
    }

    override fun failed(cause: Exception) {
        exception = cause
        cancel(CancellationException("Fail to execute request", cause))
        releaseContinuation(Result.failure(cause))
    }

    override fun cancel(): Boolean {
        val cancellationException = CancellationException("Request canceled", null)

        exception = cancellationException
        coroutineContext.cancel()
        releaseContinuation(Result.failure(cancellationException))
        return true
    }

    private suspend fun inDecoderThread(block: (ContentDecoder) -> Unit) {
        val decoder = suspendCoroutineUninterceptedOrReturn<ContentDecoder?> {
            continuation.value = it
            requestInput()

            if (completed.value && continuation.compareAndSet(it, null)) {
                null
            } else {
                COROUTINE_SUSPENDED
            }
        }

        decoder ?: return
        block(decoder)
    }


    override fun getResult() = Unit

    override fun responseCompleted(context: HttpContext?) {
        releaseContinuation(Result.success(null))
    }

    private fun releaseContinuation(result: Result<ContentDecoder?>) {
        completed.value = true
        val continuation = continuation.getAndSet(null) ?: return
        continuation.resumeWith(result)
    }

    override fun getException(): Exception? = exception

    override fun close() {
    }

    private fun requestInput() {
        if (!suspended.compareAndSet(true, false)) return
        control.requestInput()
    }

    private fun suspendInput() {
        if (!suspended.compareAndSet(false, true)) return
        control.suspendInput()
    }
}
