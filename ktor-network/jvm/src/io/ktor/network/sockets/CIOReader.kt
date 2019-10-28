/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.core.*
import io.ktor.utils.io.nio.*
import io.ktor.utils.io.pool.*
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
            while (true) {
                var rc = 0

                withSocketTimeout(socketOptions?.socketTimeout ?: INFINITE_TIMEOUT_MS) {
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

@UseExperimental(ExperimentalIoApi::class)
internal fun CoroutineScope.attachForReadingDirectImpl(
    channel: ByteChannel,
    nioChannel: ReadableByteChannel,
    selectable: Selectable,
    selector: SelectorManager,
    socketOptions: SocketOptions.TCPClientSocketOptions? = null
): WriterJob = writer(Dispatchers.Unconfined + CoroutineName("cio-from-nio-reader"), channel) {
    try {
        selectable.interestOp(SelectInterest.READ, false)

        channel.writeSuspendSession {
            while (true) {
                var rc = 0

                withSocketTimeout(socketOptions?.socketTimeout ?: INFINITE_TIMEOUT_MS) {
                    do {
                        val buffer = request(1)
                        if (buffer == null) {
                            if (channel.isClosedForWrite) break
                            channel.flush()
                            tryAwait(1)
                            continue
                        }

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
