/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.core.*
import io.ktor.utils.io.nio.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
import java.nio.*
import java.nio.channels.*

internal fun CoroutineScope.attachForReadingImpl(
    channel: ByteChannel,
    nioChannel: ReadableByteChannel,
    selectable: Selectable,
    selector: SelectorManager,
    pool: ObjectPool<ByteBuffer>,
    socketOptions: SocketOptions.TCPClientSocketOptions? = null
): WriterJob {
    val buffer = pool.borrow()
    return writer(Dispatchers.Unconfined + CoroutineName("cio-from-nio-reader"), channel) {
        try {
            val timeout = if (socketOptions?.socketTimeout != null) {
                createTimeout("reading", socketOptions.socketTimeout) {
                    channel.close(SocketTimeoutException())
                }
            } else {
                null
            }

            while (true) {
                var rc = 0

                timeout.withTimeout {
                    do {
                        rc = nioChannel.read(buffer)
                        if (rc == 0) {
                            channel.flush()
                            selectable.interestOp(SelectInterest.READ, true)
                            selector.select(selectable, SelectInterest.READ)
                        }
                    } while (rc == 0)
                }

                if (rc == -1) {
                    channel.close()
                    break
                } else {
                    selectable.interestOp(SelectInterest.READ, false)
                    buffer.flip()
                    channel.writeFully(buffer)
                    buffer.clear()
                }
            }
            timeout?.finish()
        } finally {
            pool.recycle(buffer)
            if (nioChannel is SocketChannel) {
                try {
                    nioChannel.socket().shutdownInput()
                } catch (ignore: ClosedChannelException) {
                }
            }
        }
    }
}

@OptIn(ExperimentalIoApi::class)
internal fun CoroutineScope.attachForReadingDirectImpl(
    channel: ByteChannel,
    nioChannel: ReadableByteChannel,
    selectable: Selectable,
    selector: SelectorManager,
    socketOptions: SocketOptions.TCPClientSocketOptions? = null
): WriterJob = writer(Dispatchers.Unconfined + CoroutineName("cio-from-nio-reader"), channel) {
    try {
        selectable.interestOp(SelectInterest.READ, false)

        val timeout = if (socketOptions?.socketTimeout != null) {
            createTimeout("reading-direct", socketOptions.socketTimeout) {
                channel.close(SocketTimeoutException())
            }
        } else {
            null
        }
        channel.writeSuspendSession {
            while (true) {
                var rc = 0
                val buffer = request(1)
                if (buffer == null) {
                    if (channel.isClosedForWrite) break
                    channel.flush()
                    tryAwait(1)
                    continue
                }

                timeout.withTimeout {
                    do {
                        rc = nioChannel.read(buffer)
                        if (rc == 0) {
                            channel.flush()
                            selectable.interestOp(SelectInterest.READ, true)
                            selector.select(selectable, SelectInterest.READ)
                        }
                    } while (rc == 0)
                }
                if (rc == -1) {
                    break
                } else {
                    written(rc)
                }
            }
        }

        timeout?.finish()
        channel.close()
    } finally {
        if (nioChannel is SocketChannel) {
            try {
                nioChannel.socket().shutdownInput()
            } catch (ignore: ClosedChannelException) {
            }
        }
    }
}
