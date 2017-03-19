package org.jetbrains.ktor.netty

import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.nio.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.transform.*
import java.util.concurrent.*

/**
 * [ApplicationHost] implementation for running standalone Netty Host
 */
class NettyApplicationHost(override val hostConfig: ApplicationHostConfig,
                           val environment: ApplicationEnvironment,
                           val applicationLifecycle: ApplicationLifecycle) : ApplicationHostStartable {

    val application: Application get() = applicationLifecycle.application

    constructor(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment)
            : this(hostConfig, environment, ApplicationLoader(environment, hostConfig.autoreload))

    private val parallelism = Runtime.getRuntime().availableProcessors() / 3 + 1
    private val connectionEventGroup = NettyConnectionPool(parallelism) // accepts connections
    internal val workerEventGroup = NettyWorkerPool(parallelism) // processes socket data and parse HTTP
    internal val callEventGroup = NettyCallPool(parallelism) // processes calls

    private var channels: List<Channel>? = null
    private val bootstraps = hostConfig.connectors.map { connector ->
        ServerBootstrap().apply {
            group(connectionEventGroup, workerEventGroup)
            channel(NioServerSocketChannel::class.java)
            childHandler(NettyChannelInitializer(this@NettyApplicationHost, connector))
        }
    }

    val pipeline = defaultHostPipeline(environment)

    init {
        applicationLifecycle.onBeforeInitializeApplication {
            install(ApplicationTransform).registerDefaultHandlers()
        }
    }

    override fun start(wait: Boolean) : NettyApplicationHost {
        applicationLifecycle.ensureApplication()
        environment.log.trace("Starting server…")
        channels = bootstraps.zip(hostConfig.connectors)
                .map { it.first.bind(it.second.host, it.second.port) }
                .map { it.sync().channel() }
        environment.log.trace("Server running.")

        if (wait) {
            channels?.map { it.closeFuture() }?.forEach { it.sync() }
            stop(1, 5, TimeUnit.SECONDS)
        }
        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        environment.log.trace("Stopping server…")
        channels?.forEach { it.close().sync() }
        val shutdownConnections = connectionEventGroup.shutdownGracefully(gracePeriod, timeout, timeUnit)
        val shutdownWorkers = workerEventGroup.shutdownGracefully(gracePeriod, timeout, timeUnit)
        val shutdownCall = callEventGroup.shutdownGracefully(gracePeriod, timeout, timeUnit)
        shutdownConnections.await()
        shutdownWorkers.await()
        shutdownCall.await()

        applicationLifecycle.dispose()
        environment.log.trace("Server stopped.")
    }

    override fun toString(): String {
        return "Netty($hostConfig)"
    }
}

class NettyConnectionPool(parallelism: Int) : NioEventLoopGroup(parallelism)
class NettyWorkerPool(parallelism: Int) : NioEventLoopGroup(parallelism)
class NettyCallPool(parallelism: Int) : NioEventLoopGroup(parallelism)