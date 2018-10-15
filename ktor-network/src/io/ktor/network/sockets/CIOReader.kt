package io.ktor.network.sockets

import io.ktor.network.selector.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.coroutines.io.ByteChannel
import kotlinx.io.core.*
import kotlinx.io.nio.*
import kotlinx.io.pool.*
import java.nio.*
import java.nio.channels.*

internal fun CoroutineScope.attachForReadingImpl(
    channel: ByteChannel,
    nioChannel: ReadableByteChannel,
    selectable: Selectable,
    selector: SelectorManager,
    pool: ObjectPool<ByteBuffer>
): WriterJob {
    val buffer = pool.borrow()
    return writer(Dispatchers.Unconfined, channel) {
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

@UseExperimental(ExperimentalIoApi::class)
internal fun CoroutineScope.attachForReadingDirectImpl(
    channel: ByteChannel,
    nioChannel: ReadableByteChannel,
    selectable: Selectable,
    selector: SelectorManager
): WriterJob = writer(Dispatchers.Unconfined, channel) {
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
