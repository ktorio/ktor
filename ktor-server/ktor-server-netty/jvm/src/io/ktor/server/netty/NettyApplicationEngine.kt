package io.ktor.server.netty

import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.nio.*
import kotlinx.coroutines.*
import io.netty.handler.codec.http.*
import java.util.concurrent.*

/**
 * [ApplicationEngine] implementation for running in a standalone Netty
 */
class NettyApplicationEngine(environment: ApplicationEngineEnvironment, configure: Configuration.() -> Unit = {}) :
    BaseApplicationEngine(environment) {

    /**
     * Configuration for the [NettyApplicationEngine]
     */
    class Configuration : BaseApplicationEngine.Configuration() {
        /**
         * Size of the queue to store [ApplicationCall] instances that cannot be immediately processed
         */
        var requestQueueLimit: Int = 16

        /**
         * Number of concurrently running requests from the same http pipeline
         */
        var runningLimit: Int = 10

        /**
         * Do not create separate call event group and reuse worker group for processing calls
         */
        var shareWorkGroup: Boolean = false

        /**
         * User-provided function to configure Netty's [ServerBootstrap]
         */
        var configureBootstrap: ServerBootstrap.() -> Unit = {}

        /**
         * Timeout in seconds for sending responses to client
         */
        var responseWriteTimeoutSeconds: Int = 10

        /**
         * User-provided function to configure Netty's [HttpServerCodec]
         */
        var httpServerCodec: () -> HttpServerCodec = ::HttpServerCodec
    }

    private val configuration = Configuration().apply(configure)

    // accepts connections
    private val connectionEventGroup = NettyConnectionPool(configuration.connectionGroupSize)

    // processes socket data and parse HTTP, may also process calls if shareWorkGroup is true
    private val workerEventGroup = if (configuration.shareWorkGroup)
        NettyWorkerPool(configuration.workerGroupSize + configuration.callGroupSize)
    else
        NettyWorkerPool(configuration.workerGroupSize)

    // processes calls
    private val callEventGroup = if (configuration.shareWorkGroup)
        workerEventGroup
    else
        NettyCallPool(configuration.callGroupSize)

    private val dispatcherWithShutdown = DispatcherWithShutdown(NettyDispatcher)
    private val engineDispatcherWithShutdown = DispatcherWithShutdown(workerEventGroup.asCoroutineDispatcher())
    private var cancellationDeferred: CompletableJob? = null

    private var channels: List<Channel>? = null
    private val bootstraps = environment.connectors.map { connector ->
        ServerBootstrap().apply {
            configuration.configureBootstrap(this)
            group(connectionEventGroup, workerEventGroup)
            channel(NioServerSocketChannel::class.java)
            childHandler(
                NettyChannelInitializer(
                    pipeline, environment,
                    callEventGroup, engineDispatcherWithShutdown, dispatcherWithShutdown,
                    connector,
                    configuration.requestQueueLimit,
                    configuration.runningLimit,
                    configuration.responseWriteTimeoutSeconds,
                    configuration.httpServerCodec
                )
            )
        }
    }

    init {
        val afterCall = PipelinePhase("After")
        pipeline.insertPhaseAfter(EnginePipeline.Call, afterCall)
        pipeline.intercept(afterCall) {
            (call as? NettyApplicationCall)?.finish()
        }
    }

    override fun start(wait: Boolean): NettyApplicationEngine {
        environment.start()

        channels = bootstraps.zip(environment.connectors)
            .map { it.first.bind(it.second.host, it.second.port) }
            .map { it.sync().channel() }

        cancellationDeferred = stopServerOnCancellation()

        if (wait) {
            channels?.map { it.closeFuture() }?.forEach { it.sync() }
            stop(1, 5, TimeUnit.SECONDS)
        }
        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        cancellationDeferred?.complete()
        environment.monitor.raise(ApplicationStopPreparing, environment)
        val channelFutures = channels?.mapNotNull { if (it.isOpen) it.close() else null }.orEmpty()

        dispatcherWithShutdown.prepareShutdown()
        engineDispatcherWithShutdown.prepareShutdown()
        try {
            val shutdownConnections = connectionEventGroup.shutdownGracefully(gracePeriod, timeout, timeUnit)
            shutdownConnections.await()

            val shutdownWorkers = workerEventGroup.shutdownGracefully(gracePeriod, timeout, timeUnit)
            if (configuration.shareWorkGroup) {
                shutdownWorkers.await()
            } else {
                val shutdownCall = callEventGroup.shutdownGracefully(gracePeriod, timeout, timeUnit)
                shutdownWorkers.await()
                shutdownCall.await()
            }

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

/**
 * [NioEventLoopGroup] for accepting connections
 */
class NettyConnectionPool(parallelism: Int) : NioEventLoopGroup(parallelism)

/**
 * [NioEventLoopGroup] for processing incoming requests and doing engine's internal work
 */
class NettyWorkerPool(parallelism: Int) : NioEventLoopGroup(parallelism)

/**
 * [NioEventLoopGroup] for processing [ApplicationCall] instances
 */
class NettyCallPool(parallelism: Int) : NioEventLoopGroup(parallelism)
