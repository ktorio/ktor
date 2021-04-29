/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.*
import java.net.http.*
import java.nio.*
import java.util.concurrent.*
import kotlin.coroutines.*

internal class JavaHttpResponseBodyHandler(
    private val coroutineContext: CoroutineContext,
    private val requestTime: GMTDate = GMTDate()
) : HttpResponse.BodyHandler<HttpResponseData> {

    override fun apply(responseInfo: HttpResponse.ResponseInfo): HttpResponse.BodySubscriber<HttpResponseData> {
        return JavaHttpResponseBodySubscriber(coroutineContext, responseInfo, requestTime)
    }

    private class JavaHttpResponseBodySubscriber(
        private val callContext: CoroutineContext,
        response: HttpResponse.ResponseInfo,
        requestTime: GMTDate
    ) : HttpResponse.BodySubscriber<HttpResponseData>, CoroutineScope {

        private val consumerJob = Job(callContext[Job])
        override val coroutineContext: CoroutineContext = callContext + consumerJob
        private val responseChannel = ByteChannel().apply {
            attachJob(consumerJob)
        }

        private val httpResponse = HttpResponseData(
            HttpStatusCode.fromValue(response.statusCode()),
            requestTime,
            HeadersImpl(response.headers().map()),
            when (val version = response.version()) {
                HttpClient.Version.HTTP_1_1 -> HttpProtocolVersion.HTTP_1_1
                HttpClient.Version.HTTP_2 -> HttpProtocolVersion.HTTP_2_0
                else -> throw IllegalStateException("Unknown HTTP protocol version ${version.name}")
            },
            responseChannel,
            callContext
        )

        private val closed = atomic(false)
        private val subscription = atomic<Flow.Subscription?>(null)

        private val queue = Channel<ByteBuffer>(Channel.UNLIMITED)

        init {
            launch {
                try {
                    queue.consume {
                        while (isActive) {
                            var buffer = queue.poll()
                            if (buffer == null) {
                                subscription.value?.request(1)
                                buffer = queue.receive()
                            }

                            responseChannel.writeFully(buffer)
                        }
                    }
                } catch (_: ClosedReceiveChannelException) {
                }
            }.apply {
                invokeOnCompletion {
                    responseChannel.close(it)
                    consumerJob.complete()
                }
            }
        }

        override fun onSubscribe(s: Flow.Subscription) {
            try {
                if (!subscription.compareAndSet(null, s)) {
                    s.cancel()
                    return
                }

                // check whether the stream is already closed.
                // if so, we should cancel the subscription
                // immediately.
                if (closed.value) {
                    s.cancel()
                } else {
                    s.request(1)
                }
            } catch (cause: Throwable) {
                try {
                    close(cause)
                } catch (ignored: IOException) {
                    // OK
                } finally {
                    onError(cause)
                }
            }
        }

        override fun onNext(items: List<ByteBuffer>) {
            items.forEach {
                if (it.hasRemaining()) {
                    queue.offer(it)
                }
            }
        }

        override fun onError(cause: Throwable) {
            close(cause)
        }

        override fun onComplete() {
            subscription.getAndSet(null)
            queue.close()
        }

        override fun getBody(): CompletionStage<HttpResponseData> {
            return CompletableFuture.completedStage(httpResponse)
        }

        private fun close(cause: Throwable) {
            if (!closed.compareAndSet(expect = false, update = true)) {
                return
            }

            try {
                queue.close(cause)
                subscription.getAndSet(null)?.cancel()
            } finally {
                consumerJob.completeExceptionally(cause)
                responseChannel.cancel(cause)
            }
        }
    }
}
