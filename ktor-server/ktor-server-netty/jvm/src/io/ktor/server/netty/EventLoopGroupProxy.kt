/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.netty.channel.EventLoopGroup
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollIoHandler
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueIoHandler
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.uring.IoUring
import io.netty.channel.uring.IoUringIoHandler
import io.netty.util.concurrent.DefaultThreadFactory
import io.netty.util.concurrent.EventExecutor
import io.netty.util.concurrent.Ticker
import java.util.*
import java.util.function.Consumer
import kotlin.reflect.KClass

/**
 * Transparently creates [EventLoopGroup] using io_uring, epoll or kqueue in that order when available,
 * falling back to [NioIoHandler] otherwise.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.EventLoopGroupProxy)
 */
public class EventLoopGroupProxy(
    public val channel: KClass<out ServerSocketChannel>,
    private val group: EventLoopGroup
) : EventLoopGroup by group {
    override fun ticker(): Ticker? {
        return group.ticker()
    }

    override fun forEach(action: Consumer<in EventExecutor>) {
        group.forEach(action)
    }

    override fun spliterator(): Spliterator<EventExecutor> {
        return group.spliterator()
    }

    public companion object {

        public fun create(parallelism: Int): EventLoopGroupProxy {
            val defaultFactory = DefaultThreadFactory(EventLoopGroupProxy::class.java, true)
            val ioHandlerFactory = when {
                IoUring.isAvailable() -> IoUringIoHandler.newFactory()
                Epoll.isAvailable() -> EpollIoHandler.newFactory()
                KQueue.isAvailable() -> KQueueIoHandler.newFactory()
                else -> NioIoHandler.newFactory()
            }

            return EventLoopGroupProxy(
                getChannelClass(),
                MultiThreadIoEventLoopGroup(parallelism, defaultFactory, ioHandlerFactory)
            )
        }
    }
}
