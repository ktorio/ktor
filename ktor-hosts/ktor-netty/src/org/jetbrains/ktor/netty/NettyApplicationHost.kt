package org.jetbrains.ktor.netty

import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.nio.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*

/**
 * [ApplicationHost] implementation for running standalone Netty Host
 */
class NettyApplicationHost(environment: ApplicationHostEnvironment) : BaseApplicationHost(environment) {

    private val parallelism = Runtime.getRuntime().availableProcessors() / 3 + 1
    private val connectionEventGroup = NettyConnectionPool(parallelism) // accepts connections
    internal val workerEventGroup = NettyWorkerPool(parallelism) // processes socket data and parse HTTP
    internal val callEventGroup = NettyCallPool(parallelism) // processes calls

    internal val dispatcherWithShutdown = DispatcherWithShutdown(NettyDispatcher)
    internal val hostDispatcherWithShutdown = DispatcherWithShutdown(workerEventGroup.asCoroutineDispatcher())

    private var channels: List<Channel>? = null
    private val bootstraps = environment.connectors.map { connector ->
        ServerBootstrap().apply {
            group(connectionEventGroup, workerEventGroup)
            channel(NioServerSocketChannel::class.java)
            childHandler(NettyChannelInitializer(this@NettyApplicationHost, connector))
        }
    }

    override fun start(wait: Boolean): NettyApplicationHost {
        environment.start()
        channels = bootstraps.zip(environment.connectors)
                .map { it.first.bind(it.second.host, it.second.port) }
                .map { it.sync().channel() }

        if (wait) {
            channels?.map { it.closeFuture() }?.forEach { it.sync() }
            stop(1, 5, TimeUnit.SECONDS)
        }
        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        channels?.forEach { it.close().sync() }

        dispatcherWithShutdown.prepareShutdown()
        hostDispatcherWithShutdown.prepareShutdown()
        try {

            val shutdownConnections = connectionEventGroup.shutdownGracefully(gracePeriod, timeout, timeUnit)
            val shutdownWorkers = workerEventGroup.shutdownGracefully(gracePeriod, timeout, timeUnit)
            val shutdownCall = callEventGroup.shutdownGracefully(gracePeriod, timeout, timeUnit)

            shutdownConnections.await()
            shutdownWorkers.await()
            shutdownCall.await()

            environment.stop()
        } finally {
            dispatcherWithShutdown.completeShutdown()
            hostDispatcherWithShutdown.completeShutdown()
        }
    }

    override fun toString(): String {
        return "Netty($environment)"
    }
}

class NettyConnectionPool(parallelism: Int) : NioEventLoopGroup(parallelism)
class NettyWorkerPool(parallelism: Int) : NioEventLoopGroup(parallelism)
class NettyCallPool(parallelism: Int) : NioEventLoopGroup(parallelism)