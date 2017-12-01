package io.ktor.server.netty

import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.nio.*
import kotlinx.coroutines.experimental.*
import java.util.concurrent.*

/**
 * [ApplicationEngine] implementation for running in a standalone Netty
 */
class NettyApplicationEngine(environment: ApplicationEngineEnvironment, configure: Configuration.() -> Unit = {}) : BaseApplicationEngine(environment) {
    class Configuration : BaseApplicationEngine.Configuration() {
        var requestQueueLimit: Int = 16
        var configureBootstrap: ServerBootstrap.() -> Unit = {}
        var responseWriteTimeoutSeconds: Int = 10
    }

    private val configuration = Configuration().apply(configure)
    private val connectionEventGroup = NettyConnectionPool(configuration.connectionGroupSize) // accepts connections
    private val workerEventGroup = NettyWorkerPool(configuration.workerGroupSize) // processes socket data and parse HTTP
    private val callEventGroup = NettyCallPool(configuration.callGroupSize) // processes calls

    private val dispatcherWithShutdown = DispatcherWithShutdown(NettyDispatcher)
    private val engineDispatcherWithShutdown = DispatcherWithShutdown(workerEventGroup.asCoroutineDispatcher())


    private var channels: List<Channel>? = null
    private val bootstraps = environment.connectors.map { connector ->
        ServerBootstrap().apply {
            configuration.configureBootstrap(this)
            group(connectionEventGroup, workerEventGroup)
            channel(NioServerSocketChannel::class.java)
            childHandler(NettyChannelInitializer(pipeline, environment,
                    callEventGroup, engineDispatcherWithShutdown, dispatcherWithShutdown,
                    connector, configuration.requestQueueLimit,
                    configuration.responseWriteTimeoutSeconds))
        }
    }

    override fun start(wait: Boolean): NettyApplicationEngine {
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
        environment.monitor.raise(ApplicationStopPreparing, environment)
        val channelFutures = channels?.map { it.close() }.orEmpty()

        dispatcherWithShutdown.prepareShutdown()
        engineDispatcherWithShutdown.prepareShutdown()
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
            engineDispatcherWithShutdown.completeShutdown()

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