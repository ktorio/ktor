/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.ByteChannel
import kotlinx.io.core.*
import kotlinx.io.pool.*
import java.nio.*
import java.nio.channels.*

internal fun CoroutineScope.attachForWritingImpl(
    channel: ByteChannel,
    nioChannel: WritableByteChannel,
    selectable: Selectable,
    selector: SelectorManager,
    pool: ObjectPool<ByteBuffer>
): ReaderJob {
    val buffer = pool.borrow()

    return reader(Dispatchers.Unconfined, channel) {
        try {
            while (true) {
                buffer.clear()
                if (channel.readAvailable(buffer) == -1) {
                    break
                }
                buffer.flip()

                while (buffer.hasRemaining()) {
                    val rc = nioChannel.write(buffer)
                    if (rc == 0) {
                        selectable.interestOp(SelectInterest.WRITE, true)
                        selector.select(selectable, SelectInterest.WRITE)
                    } else {
                        selectable.interestOp(SelectInterest.WRITE, false)
                    }
                }
            }
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

@UseExperimental(ExperimentalIoApi::class)
internal fun CoroutineScope.attachForWritingDirectImpl(
    channel: ByteChannel,
    nioChannel: WritableByteChannel,
    selectable: Selectable,
    selector: SelectorManager
): ReaderJob = reader(Dispatchers.Unconfined, channel) {
    selectable.interestOp(SelectInterest.WRITE, false)
    try {
        channel.lookAheadSuspend {
            while (true) {
                val buffer = request(0, 1)
                if (buffer == null) {
//                        if (channel.isClosedForRead) break
                    if (!awaitAtLeast(1)) break
                    continue
                }

                while (buffer.hasRemaining()) {
                    val r = nioChannel.write(buffer)

                    if (r == 0) {
                        selectable.interestOp(SelectInterest.WRITE, true)
                        selector.select(selectable, SelectInterest.WRITE)
                    } else {
                        consumed(r)
                    }
                }
            }
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
