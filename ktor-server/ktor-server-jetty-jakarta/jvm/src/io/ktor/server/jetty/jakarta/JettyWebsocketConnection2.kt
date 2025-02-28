/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import org.eclipse.jetty.io.AbstractConnection
import org.eclipse.jetty.io.EndPoint
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext

internal class JettyWebsocketConnection2(
    private val endpoint: EndPoint,
    private val executor: Executor,
    private val bufferPool: ByteBufferPool,
    override val coroutineContext: CoroutineContext
): AbstractConnection(endpoint, executor), CoroutineScope {

//    private val callback = FillChannelCallback()
    private val fillableChannel = Channel<Boolean>(Channel.RENDEZVOUS)

    private val inputJob: WriterJob =
        writer(Dispatchers.IO + coroutineContext + CoroutineName("websocket-input")) {
            val buffer = bufferPool.borrow()
            try {
                while (true) {
                    fillInterested()
                    fillableChannel.receive()
                    when (endpoint.fill(buffer.flip())) {
                        -1 -> break
                        0 -> continue
                        else -> {
                            channel.writeFully(buffer)
                            buffer.compact()
                        }
                    }
                }
            } finally {
                bufferPool.recycle(buffer)
            }
        }

    private val outputJob: ReaderJob =
        reader(Dispatchers.IO + coroutineContext + CoroutineName("websocket-output")) {
            val buffer = bufferPool.borrow()
            try {
                while (true) {
                    when (channel.readAvailable(buffer)) {
                        -1 -> {
                            endpoint.close()
                            break
                        }
                        0 -> continue
                        else -> {}
                    }
                    suspendCancellableCoroutine<Unit> { continuation ->
                        endpoint.write(continuation.asCallback(), buffer.flip())
                    }
                    buffer.compact()
                }
            } finally {
                bufferPool.recycle(buffer)
            }
        }

    internal val inputChannel: ByteReadChannel = inputJob.channel
    internal val outputChannel: ByteWriteChannel = outputJob.channel

    override fun onFillable() {
        fillableChannel.trySend(true)
    }

    override fun onFillInterestedFailed(cause: Throwable?) {
        fillableChannel.close(cause)
    }

//    private inner class FillChannelCallback: IteratingCallback() {
//        val buffer: ByteBuffer = bufferPool.borrow()
//
//        override fun process(): Action {
//            val fill = endpoint.fill(buffer.flip())
//            return when (fill) {
//                -1 -> Action.SUCCEEDED
//                0 -> {
//                    fillInterested()
//                    Action.IDLE
//                }
//                else -> {
//                    launch {
//                        inputChannel.writeFully(buffer)
//                        fillInterested()
//                    }
//                    Action.SCHEDULED
//                }
//            }
//        }
//
//        override fun onCompleteSuccess() {
//            bufferPool.recycle(buffer)
//            inputChannel.close()
//            endpoint.close()
//        }
//
//        override fun onCompleteFailure(cause: Throwable?) {
//            bufferPool.recycle(buffer)
//            inputChannel.cancel(cause)
//            endpoint.close(cause)
//        }
//    }
}
