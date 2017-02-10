package org.jetbrains.ktor.netty

import io.netty.bootstrap.*
import io.netty.channel.nio.*
import io.netty.channel.socket.nio.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.transform.*
import java.util.concurrent.*
import java.util.concurrent.ForkJoinPool.*

/**
 * [ApplicationHost] implementation for running standalone Netty Host
 */
class NettyApplicationHost(override val hostConfig: ApplicationHostConfig,
                           val environment: ApplicationEnvironment,
                           val applicationLifecycle: ApplicationLifecycle) : ApplicationHost, ApplicationHostStartable {

    val application: Application get() = applicationLifecycle.application

    constructor(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment)
            : this(hostConfig, environment, ApplicationLoader(environment, hostConfig.autoreload))

    private val parallelism = 3
    private val connectionEventGroup = NettyConnectionPool(parallelism) // accepts connections
    private val workerEventGroup = NettyWorkerPool(parallelism) // processes socket data

    private val callThreadPool = ForkJoinPool(parallelism, defaultForkJoinWorkerThreadFactory, Thread.UncaughtExceptionHandler { _, throwable ->
        application.environment.log.error(throwable)
    }, true)
    internal val callDispatcher = callThreadPool.toCoroutineDispatcher() // executes call handlers

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
            install(TransformationSupport).registerDefaultHandlers()
        }
    }

    override fun start(wait: Boolean) {
        applicationLifecycle.ensureApplication()
        environment.log.trace("Starting server…")
        val channelFutures = bootstraps.zip(hostConfig.connectors).map { it.first.bind(it.second.host, it.second.port) }
        environment.log.trace("Server running.")

        if (wait) {
            channelFutures.map { it.channel().closeFuture() }.forEach { it.sync() }
            stop(1, 5, TimeUnit.SECONDS)
        }
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        environment.log.trace("Stopping server…")
        val shutdownConnections = connectionEventGroup.shutdownGracefully(gracePeriod, timeout, timeUnit)
        val shutdownWorkers = workerEventGroup.shutdownGracefully(gracePeriod, timeout, timeUnit)
        shutdownConnections.await()
        shutdownWorkers.await()

        callThreadPool.shutdown()
        applicationLifecycle.dispose()
        environment.log.trace("Server stopped.")
    }

    override fun toString(): String {
        return "Netty($hostConfig)"
    }
}

class NettyConnectionPool(parallelism: Int) : NioEventLoopGroup(parallelism)
class NettyWorkerPool(parallelism: Int) : NioEventLoopGroup(parallelism)