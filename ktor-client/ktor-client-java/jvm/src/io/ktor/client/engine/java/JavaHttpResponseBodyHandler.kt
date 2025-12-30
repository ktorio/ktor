/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Flow
import kotlin.coroutines.CoroutineContext

internal class JavaHttpResponseBodyHandler(
    private val coroutineContext: CoroutineContext,
    private val requestData: HttpRequestData,
    private val requestTime: GMTDate = GMTDate()
) : HttpResponse.BodyHandler<HttpResponseData> {

    override fun apply(responseInfo: HttpResponse.ResponseInfo): HttpResponse.BodySubscriber<HttpResponseData> {
        return JavaHttpResponseBodySubscriber(coroutineContext, requestData, responseInfo, requestTime)
    }

    @OptIn(InternalKtorApi::class)
    private class JavaHttpResponseBodySubscriber(
        callContext: CoroutineContext,
        requestData: HttpRequestData,
        response: HttpResponse.ResponseInfo,
        requestTime: GMTDate
    ) : HttpResponse.BodySubscriber<HttpResponseData>, CoroutineScope {

        private val consumerJob = Job(callContext[Job])
        override val coroutineContext: CoroutineContext = callContext + consumerJob
        private val responseChannel = ByteChannel().apply {
            attachJob(consumerJob)
        }
        val status = HttpStatusCode.fromValue(response.statusCode())
        val version = when (val version = response.version()) {
            HttpClient.Version.HTTP_1_1 -> HttpProtocolVersion.HTTP_1_1
            HttpClient.Version.HTTP_2 -> HttpProtocolVersion.HTTP_2_0
            else -> throw IllegalStateException("Unknown HTTP protocol version ${version.name}")
        }
        val headerValues = response.headers().map().let {
            if (version == HttpProtocolVersion.HTTP_2_0) {
                it.filterKeys { !it.startsWith(":") }
            } else {
                it
            }
        }

        val headers = HeadersImpl(headerValues)

        val body: Any = requestData.attributes.getOrNull(ResponseAdapterAttributeKey)
            ?.adapt(requestData, status, headers, responseChannel, requestData.body, callContext)
            ?: responseChannel

        private val httpResponse = HttpResponseData(
            status,
            requestTime,
            headers,
            version,
            body,
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
                            var buffer = queue.tryReceive().getOrNull()
                            if (buffer == null) {
                                subscription.value?.request(1)
                                buffer = queue.receive()
                            }

                            responseChannel.writeFully(buffer)
                            responseChannel.flush()
                        }
                    }
                } catch (_: ClosedReceiveChannelException) {
                }
            }.apply {
                invokeOnCompletion {
                    close(it)
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
                    queue.trySend(it).isSuccess
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

        private fun close(cause: Throwable?) {
            if (!closed.compareAndSet(expect = false, update = true)) {
                return
            }

            try {
                queue.close(cause)
                subscription.getAndSet(null)?.cancel()
            } finally {
                cause?.let(consumerJob::completeExceptionally) ?: consumerJob.complete()
                responseChannel.cancel(cause)
            }
        }
    }
}
