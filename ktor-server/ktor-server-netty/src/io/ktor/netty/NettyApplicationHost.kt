package io.ktor.netty

import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.nio.*
import kotlinx.coroutines.experimental.*
import io.ktor.host.*
import io.ktor.util.*
import java.util.concurrent.*

/**
 * [ApplicationHost] implementation for running standalone Netty Host
 */
class NettyApplicationHost(environment: ApplicationHostEnvironment) : BaseApplicationHost(environment) {

    private val parallelism = Runtime.getRuntime().availableProcessors() / 3 + 1
    private val connectionEventGroup = NettyConnectionPool(parallelism) // accepts connections
    private val workerEventGroup = NettyWorkerPool(parallelism) // processes socket data and parse HTTP
    private val callEventGroup = NettyCallPool(parallelism) // processes calls

    private val dispatcherWithShutdown = DispatcherWithShutdown(NettyDispatcher)
    private val hostDispatcherWithShutdown = DispatcherWithShutdown(workerEventGroup.asCoroutineDispatcher())

    private val requestQueueLimit: Int = 16 // should be configurable

    private var channels: List<Channel>? = null
    private val bootstraps = environment.connectors.map { connector ->
        ServerBootstrap().apply {
            group(connectionEventGroup, workerEventGroup)
            channel(NioServerSocketChannel::class.java)
            childHandler(NettyChannelInitializer(pipeline, environment,
                    callEventGroup, hostDispatcherWithShutdown, dispatcherWithShutdown,
                    connector, requestQueueLimit))
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
        val channelFutures = channels?.map { it.close() }.orEmpty()

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

            channelFutures.forEach { it.sync() }
        }
    }

    override fun toString(): String {
        return "Netty($environment)"
    }
}

class NettyConnectionPool(parallelism: Int) : NioEventLoopGroup(parallelism)
class NettyWorkerPool(parallelism: Int) : NioEventLoopGroup(parallelism)
class NettyCallPool(parallelism: Int) : NioEventLoopGroup(parallelism)