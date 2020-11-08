/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.java

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
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
        callContext: CoroutineContext,
        response: HttpResponse.ResponseInfo,
        requestTime: GMTDate
    ) : HttpResponse.BodySubscriber<HttpResponseData>, CoroutineScope {

        private val consumerJob = Job(callContext[Job])
        override val coroutineContext: CoroutineContext = callContext + consumerJob

        private val channel = ByteChannel().apply {
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
            channel,
            coroutineContext
        )

        private val closed = atomic(false)
        private val subscribed = atomic(false)
        private val subscription = atomic<Flow.Subscription?>(null)

        override fun onSubscribe(s: Flow.Subscription) {
            try {
                if (!subscribed.compareAndSet(expect = false, update = true)) {
                    s.cancel()
                } else {
                    // check whether the stream is already closed.
                    // if so, we should cancel the subscription
                    // immediately.
                    var closed: Boolean
                    synchronized(this) {
                        closed = this.closed.value
                        if (!closed) {
                            subscription.value = s
                        }
                    }
                    if (closed) {
                        s.cancel()
                        return
                    }
                    s.request(1)
                }
            } catch (t: Throwable) {
                try {
                    close(t)
                } catch (e: IOException) {
                    // OK
                } finally {
                    onError(t)
                }
            }
        }

        override fun onNext(items: List<ByteBuffer>) {
            runBlocking {
                try {
                    items.forEach { buffer ->
                        channel.writeFully(buffer)
                    }
                } catch (e: Throwable) {
                    close(e)
                }

                subscription.value?.request(1)
            }
        }

        override fun onError(throwable: Throwable) {
            close(throwable)
        }

        override fun onComplete() {
            subscription.getAndSet(null)
            channel.close()
            consumerJob.complete()
        }

        override fun getBody(): CompletionStage<HttpResponseData> {
            return CompletableFuture.completedStage(httpResponse)
        }

        private fun close(throwable: Throwable) {
            if (!closed.compareAndSet(expect = false, update = true)) {
                return
            }

            try {
                subscription.getAndSet(null)?.cancel()
            } finally {
                consumerJob.completeExceptionally(throwable)
                channel.close(throwable)
            }
        }
    }
}
