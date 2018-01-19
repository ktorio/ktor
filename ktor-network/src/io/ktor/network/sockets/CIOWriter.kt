package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.ByteChannel
import kotlinx.io.pool.*
import java.nio.channels.*

internal fun attachForWritingImpl(
        channel: ByteChannel,
        nioChannel: WritableByteChannel,
        selectable: Selectable,
        selector: SelectorManager,
        pool: ObjectPool<ByteBuffer>,
        parent: Job
): ReaderJob {
    val buffer = pool.borrow()

    return reader(ioCoroutineDispatcher, channel, parent) {
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
                    nioChannel.shutdownOutput()
                } catch (ignore: ClosedChannelException) {
                }
            }
        }
    }
}

internal fun attachForWritingDirectImpl(
        channel: ByteChannel,
        nioChannel: WritableByteChannel,
        selectable: Selectable,
        selector: SelectorManager,
        parent: Job
): ReaderJob {
    return reader(ioCoroutineDispatcher, channel, parent) {
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
                    nioChannel.shutdownOutput()
                } catch (ignore: ClosedChannelException) {
                }
            }
        }
    }
}