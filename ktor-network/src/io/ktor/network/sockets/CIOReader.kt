package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.ByteChannel
import kotlinx.io.pool.*
import java.nio.channels.*

internal fun attachForReadingImpl(
        channel: ByteChannel,
        nioChannel: ReadableByteChannel,
        selectable: Selectable,
        selector: SelectorManager,
        pool: ObjectPool<ByteBuffer>,
        parent: Job
): WriterJob {
    val buffer = pool.borrow()
    return writer(ioCoroutineDispatcher, channel, parent) {
        try {
            while (true) {
                val rc = nioChannel.read(buffer)
                if (rc == -1) {
                    channel.close()
                    break
                } else if (rc == 0) {
                    channel.flush()
                    selectable.interestOp(SelectInterest.READ, true)
                    selector.select(selectable, SelectInterest.READ)
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
                    nioChannel.shutdownInput()
                } catch (ignore: ClosedChannelException) {
                }
            }
        }
    }
}

internal fun attachForReadingDirectImpl(
        channel: ByteChannel,
        nioChannel: ReadableByteChannel,
        selectable: Selectable,
        selector: SelectorManager,
        parent: Job
): WriterJob {
    return writer(ioCoroutineDispatcher, channel, parent) {
        try {
            selectable.interestOp(SelectInterest.READ, false)

            channel.writeSuspendSession {
                while (true) {
                    val buffer = request(1)
                    if (buffer == null) {
                        if (channel.isClosedForWrite) break
                        channel.flush()
                        tryAwait(1)
                        continue
                    }

                    val rc = nioChannel.read(buffer)
                    if (rc == -1) {
                        break
                    } else if (rc == 0) {
                        channel.flush()
                        selectable.interestOp(SelectInterest.READ, true)
                        selector.select(selectable, SelectInterest.READ)
                    } else {
                        written(rc)
                    }
                }
            }

            channel.close()
        } finally {
            if (nioChannel is SocketChannel) {
                try {
                    nioChannel.shutdownInput()
                } catch (ignore: ClosedChannelException) {
                }
            }
        }
    }
}