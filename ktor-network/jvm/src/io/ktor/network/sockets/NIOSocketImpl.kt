/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.utils.io.*
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.pool.*
import java.nio.*
import java.nio.channels.*
import kotlin.coroutines.*

internal abstract class NIOSocketImpl<out S>(
    override val channel: S,
    val selector: SelectorManager,
    val pool: ObjectPool<ByteBuffer>?,
    private val socketOptions: SocketOptions.TCPClientSocketOptions? = null
) : ReadWriteSocket, SocketBase(EmptyCoroutineContext)
    where S : java.nio.channels.ByteChannel, S : SelectableChannel {

    // NOTE: it is important here to use different versions of attachForReadingImpl
    // because it is not always valid to use channel's internal buffer for NIO read/write:
    //  at least UDP datagram reading MUST use bigger byte buffer otherwise datagram could be truncated
    //  that will cause broken data
    // however it is not the case for attachForWriting this is why we use direct writing in any case

    final override fun attachForReadingImpl(channel: ByteChannel): WriterJob {
        return if (pool != null) {
            attachForReadingImpl(channel, this.channel, this, selector, pool, socketOptions)
        } else {
            attachForReadingDirectImpl(channel, this.channel, this, selector, socketOptions)
        }
    }

    final override fun attachForWritingImpl(channel: ByteChannel): ReaderJob {
        return attachForWritingDirectImpl(channel, this.channel, this, selector, socketOptions)
    }

    override fun actualClose(): Throwable? {
        return try {
            channel.close()
            super.close()
            null
        } catch (cause: Throwable) {
            cause
        } finally {
            selector.notifyClosed(this)
        }
    }
}
