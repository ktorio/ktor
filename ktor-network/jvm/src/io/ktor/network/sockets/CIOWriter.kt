/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import java.nio.*
import java.nio.channels.*

internal fun CoroutineScope.attachForWritingImpl(
    channel: ByteChannel,
    nioChannel: WritableByteChannel,
    selectable: Selectable,
    selector: SelectorManager,
    pool: ObjectPool<ByteBuffer>,
    socketOptions: SocketOptions.TCPClientSocketOptions? = null
): ReaderJob {
    val buffer = pool.borrow()

    return reader(Dispatchers.Unconfined + CoroutineName("cio-to-nio-writer"), channel) {
        try {
            val timeout = if (socketOptions?.socketTimeout != null) {
                createTimeout("writing", socketOptions.socketTimeout) {
                    channel.close(SocketTimeoutException())
                }
            } else {
                null
            }

            while (true) {
                buffer.clear()
                if (channel.readAvailable(buffer) == -1) {
                    break
                }
                buffer.flip()

                while (buffer.hasRemaining()) {
                    var rc: Int

                    timeout.withTimeout {
                        do {
                            rc = nioChannel.write(buffer)
                            if (rc == 0) {
                                selectable.interestOp(SelectInterest.WRITE, true)
                                selector.select(selectable, SelectInterest.WRITE)
                            }
                        } while (buffer.hasRemaining() && rc == 0)
                    }

                    selectable.interestOp(SelectInterest.WRITE, false)
                }
            }
            timeout?.finish()
        } finally {
            pool.recycle(buffer)
            if (nioChannel is SocketChannel) {
                try {
                    nioChannel.socket().shutdownOutput()
                } catch (ignore: ClosedChannelException) {
                }
            }
        }
    }
}

@OptIn(ExperimentalIoApi::class)
internal fun CoroutineScope.attachForWritingDirectImpl(
    channel: ByteChannel,
    nioChannel: WritableByteChannel,
    selectable: Selectable,
    selector: SelectorManager,
    socketOptions: SocketOptions.TCPClientSocketOptions? = null
): ReaderJob = reader(Dispatchers.Unconfined + CoroutineName("cio-to-nio-writer"), channel) {
    selectable.interestOp(SelectInterest.WRITE, false)
    try {
        @Suppress("DEPRECATION")
        channel.lookAheadSuspend {
            val timeout = if (socketOptions?.socketTimeout != null) {
                createTimeout("writing-direct", socketOptions.socketTimeout) {
                    channel.close(SocketTimeoutException())
                }
            } else {
                null
            }

            while (true) {
                val buffer = request(0, 1)
                if (buffer == null) {
//                        if (channel.isClosedForRead) break
                    if (!awaitAtLeast(1)) break
                    continue
                }

                while (buffer.hasRemaining()) {
                    var rc = 0

                    timeout.withTimeout {
                        do {
                            rc = nioChannel.write(buffer)
                            if (rc == 0) {
                                selectable.interestOp(SelectInterest.WRITE, true)
                                selector.select(selectable, SelectInterest.WRITE)
                            }
                        } while (buffer.hasRemaining() && rc == 0)
                    }

                    consumed(rc)
                }
            }
            timeout?.finish()
        }
    } finally {
        selectable.interestOp(SelectInterest.WRITE, false)
        if (nioChannel is SocketChannel) {
            try {
                nioChannel.socket().shutdownOutput()
            } catch (ignore: ClosedChannelException) {
            }
        }
    }
}
