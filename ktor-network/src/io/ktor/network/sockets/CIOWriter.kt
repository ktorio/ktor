package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.ByteChannel
import kotlinx.io.pool.*
import java.io.*
import java.nio.channels.*

internal fun attachForWritingImpl(channel: ByteChannel, nioChannel: WritableByteChannel, selectable: Selectable, selector: SelectorManager, pool: ObjectPool<ByteBuffer>): ReaderJob {
    val buffer = pool.borrow()

    return reader(ioCoroutineDispatcher, channel) {
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

internal fun attachForWritingDirectImpl(channel: ByteChannel, nioChannel: WritableByteChannel, selectable: Selectable, selector: SelectorManager): ReaderJob {
    return reader(ioCoroutineDispatcher, channel) {
        try {
            var rc: Int
            val readBlock = { buffer: ByteBuffer ->
                rc = nioChannel.write(buffer)
            }

            while (true) {
                rc = 0
                channel.read(block = readBlock)
                if (rc == 0) {
                    if (channel.isClosedForRead) break
                    selectable.interestOp(SelectInterest.WRITE, true)
                    selector.select(selectable, SelectInterest.WRITE)
                } else {
                    selectable.interestOp(SelectInterest.WRITE, false)
                }
            }
        } catch (end: EOFException) {
            selectable.interestOp(SelectInterest.WRITE, false)
        } finally {
            if (nioChannel is SocketChannel) {
                try {
                    nioChannel.shutdownOutput()
                } catch (ignore: ClosedChannelException) {
                }
            }
        }
    }
}