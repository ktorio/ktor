/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.java

import io.ktor.network.util.*
import io.ktor.utils.io.*
import jdk.internal.net.http.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import java.io.*
import java.net.http.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*
import kotlin.coroutines.*

internal class JavaHttpRequestBodyPublisher(
    private val coroutineContext: CoroutineContext,
    private val contentLength: Long = -1,
    private val getChannel: () -> ByteReadChannel
) : HttpRequest.BodyPublisher {

    override fun contentLength(): Long {
        return contentLength
    }

    override fun subscribe(subscriber: Flow.Subscriber<in ByteBuffer>) {
        println("Subscribing $subscriber")
        try {
            // We need to synchronize here because the subscriber could call
            // request() from within onSubscribe which would potentially
            // trigger onNext before onSubscribe is finished.
            val subscription = ReadableByteChannelSubscription(
                coroutineContext, getChannel(), subscriber
            )
            synchronized(subscription) { subscriber.onSubscribe(subscription) }
        } catch (cause: Exception) {
            // subscribe() must return normally, so we need to signal the
            // failure to open via onError() once onSubscribe() is signaled.
            subscriber.onSubscribe(NullSubscription())
            subscriber.onError(cause)
        }
    }

    private class ReadableByteChannelSubscription(
        override val coroutineContext: CoroutineContext,
        private val inputChannel: ByteReadChannel,
        private val subscriber: Flow.Subscriber<in ByteBuffer>
    ) : Flow.Subscription, CoroutineScope {
        private val outstandingDemand = atomic(0L)
        private var writeInProgress = false

        @Volatile
        private var done = false

        override fun request(n: Long) {
            if (done) {
                return
            }
            if (n < 1) {
                val cause = IllegalArgumentException(
                    "$subscriber violated the Reactive Streams rule 3.9 by requesting "
                        + "a non-positive number of elements."
                )
                signalOnError(cause)
            } else {
                try {
                    // As governed by rule 3.17, when demand overflows `Long.MAX_VALUE` we treat the signalled demand as
                    // "effectively unbounded"
                    outstandingDemand.getAndUpdate { initialDemand: Long ->
                        if (Long.MAX_VALUE - initialDemand < n) {
                            Long.MAX_VALUE
                        } else {
                            initialDemand + n
                        }
                    }
                    synchronized(this) {
                        if (!writeInProgress) {
                            writeInProgress = true
                            readData()
                        }
                    }
                } catch (cause: Exception) {
                    signalOnError(cause)
                }
            }
        }

        override fun cancel() {
            synchronized(this) {
                if (!done) {
                    done = true
                    closeChannel()
                }
            }
        }

        private fun readData() {
            // It's possible to have another request for data come in after we've closed the file.
            if (inputChannel.isClosedForRead) {
                return
            }

            launch {
                do {
                    val buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
                    val result = try {
                        inputChannel.readAvailable(buffer)
                    } catch (cause: Throwable) {
                        signalOnError(cause)
                        closeChannel()
                        return@launch
                    }

                    if (result > 0) {
                        buffer.flip()
                        signalOnNext(buffer)
                    }
                    // If we have more permits, queue up another read.
                } while (outstandingDemand.decrementAndGet() > 0)

                if (inputChannel.isClosedForRead) {
                    // Reached the end of the channel, notify the subscriber and cleanup
                    signalOnComplete()
                    closeChannel()
                }

                synchronized(this) {
                    writeInProgress = false
                }
            }
        }

        private fun closeChannel() {
            try {
                inputChannel.cancel()
            } catch (cause: Exception) {
                signalOnError(cause)
            }
        }

        private fun signalOnNext(bb: ByteBuffer) {
            synchronized(this) {
                if (!done) {
                    subscriber.onNext(bb)
                }
            }
        }

        private fun signalOnComplete() {
            synchronized(this) {
                if (!done) {
                    subscriber.onComplete()
                    done = true
                }
            }
        }

        private fun signalOnError(cause: Throwable) {
            synchronized(this) {
                if (!done) {
                    subscriber.onError(cause)
                    done = true
                }
            }
        }
    }

    private class NullSubscription : Flow.Subscription {
        override fun request(n: Long) {}
        override fun cancel() {}
    }
}
