/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.util.network.*
import io.ktor.util.pipeline.*
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoopGroup
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueServerSocketChannel
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectDecoder
import io.netty.handler.codec.http.HttpServerCodec
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.net.BindException
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

private val AFTER_CALL_PHASE = PipelinePhase("After")

/**
 * [ApplicationEngine] implementation for running in a standalone Netty
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine)
 */
public class NettyApplicationEngine(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    public val configuration: Configuration,
    private val applicationProvider: () -> Application
) : BaseApplicationEngine(environment, monitor, developmentMode) {

    /**
     * Configuration for the [NettyApplicationEngine]
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine.Configuration)
     */
    public class Configuration : BaseApplicationEngine.Configuration() {

        /**
         * Number of concurrently running requests from the same http pipeline
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine.Configuration.runningLimit)
         */
        public var runningLimit: Int = 32

        /**
         * Do not create separate call event group and reuse worker group for processing calls
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine.Configuration.shareWorkGroup)
         */
        public var shareWorkGroup: Boolean = false

        /**
         * User-provided function to configure Netty's [ServerBootstrap]
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine.Configuration.configureBootstrap)
         */
        public var configureBootstrap: ServerBootstrap.() -> Unit = {}

        /**
         * Timeout in seconds for sending responses to client
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine.Configuration.responseWriteTimeoutSeconds)
         */
        public var responseWriteTimeoutSeconds: Int = 10

        /**
         * Timeout in seconds for reading requests from client, "0" is infinite.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine.Configuration.requestReadTimeoutSeconds)
         */
        public var requestReadTimeoutSeconds: Int = 0

        /**
         * If set to `true`, enables TCP keep alive for connections so all
         * dead client connections will be discarded.
         * The timeout period is configured by the system so configure
         * your host accordingly.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine.Configuration.tcpKeepAlive)
         */
        public var tcpKeepAlive: Boolean = false

        /**
         * The url limit including query parameters
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine.Configuration.maxInitialLineLength)
         */
        public var maxInitialLineLength: Int = HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH

        /**
         * The maximum length of all headers.
         * If the sum of the length of each header exceeds this value, a TooLongFrameException will be raised.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine.Configuration.maxHeaderSize)
         */
        public var maxHeaderSize: Int = HttpObjectDecoder.DEFAULT_MAX_HEADER_SIZE

        /**
         * The maximum length of the content or each chunk
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine.Configuration.maxChunkSize)
         */
        public var maxChunkSize: Int = HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE

        /**
         * If set to `true`, enables HTTP/2 protocol for Netty engine
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine.Configuration.enableHttp2)
         */
        public var enableHttp2: Boolean = true

        /**
         * User-provided function to configure Netty's [HttpServerCodec]
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine.Configuration.httpServerCodec)
         */
        public var httpServerCodec: () -> HttpServerCodec = this::defaultHttpServerCodec

        /**
         * User-provided function to configure Netty's [ChannelPipeline]
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine.Configuration.channelPipelineConfig)
         */
        public var channelPipelineConfig: ChannelPipeline.() -> Unit = {}

        /**
         * Default function to configure Netty's
         */
        private fun defaultHttpServerCodec() = HttpServerCodec(
            maxInitialLineLength,
            maxHeaderSize,
            maxChunkSize
        )
    }

    /**
     * [EventLoopGroupProxy] for accepting connections
     */
    private val connectionEventGroup: EventLoopGroup by lazy {
        customBootstrap.config().group() ?: EventLoopGroupProxy.create(configuration.connectionGroupSize)
    }

    /**
     * [EventLoopGroupProxy] for processing incoming requests and doing engine's internal work
     */
    private val workerEventGroup: EventLoopGroup by lazy {
        customBootstrap.config().childGroup()?.let {
            return@lazy it
        }
        if (configuration.shareWorkGroup) {
            EventLoopGroupProxy.create(configuration.workerGroupSize + configuration.callGroupSize)
        } else {
            EventLoopGroupProxy.create(configuration.workerGroupSize)
        }
    }

    private val customBootstrap: ServerBootstrap by lazy {
        ServerBootstrap().apply(configuration.configureBootstrap)
    }

    /**
     * [EventLoopGroupProxy] for processing [PipelineCall] instances
     */
    private val callEventGroup: EventLoopGroup by lazy {
        if (configuration.shareWorkGroup) {
            workerEventGroup
        } else {
            EventLoopGroupProxy.create(configuration.callGroupSize)
        }
    }

    private val nettyDispatcher: CoroutineDispatcher by lazy {
        NettyDispatcher
    }

    private val workerDispatcher by lazy {
        workerEventGroup.asCoroutineDispatcher()
    }

    private var cancellationJob: CompletableJob? = null

    private var channels: List<Channel>? = null
    internal val bootstraps: List<ServerBootstrap> by lazy {
        configuration.connectors.map(::createBootstrap)
    }

    private val userContext = applicationProvider().parentCoroutineContext +
        nettyDispatcher +
        NettyApplicationCallHandler.CallHandlerCoroutineName +
        DefaultUncaughtExceptionHandler(environment.log)

    private fun createBootstrap(connector: EngineConnectorConfig): ServerBootstrap {
        return customBootstrap.clone().apply {
            if (config().group() == null && config().childGroup() == null) {
                group(connectionEventGroup, workerEventGroup)
            }

            if (config().channelFactory() == null) {
                channel(getChannelClass().java)
            }

            childHandler(
                NettyChannelInitializer(
                    applicationProvider,
                    pipeline,
                    environment,
                    callEventGroup,
                    workerDispatcher,
                    userContext,
                    connector,
                    configuration.runningLimit,
                    configuration.responseWriteTimeoutSeconds,
                    configuration.requestReadTimeoutSeconds,
                    configuration.httpServerCodec,
                    configuration.channelPipelineConfig,
                    configuration.enableHttp2
                )
            )
            if (configuration.tcpKeepAlive) {
                childOption(ChannelOption.SO_KEEPALIVE, true)
            }
        }
    }

    init {
        pipeline.insertPhaseAfter(EnginePipeline.Call, AFTER_CALL_PHASE)
        pipeline.intercept(AFTER_CALL_PHASE) {
            (call as? NettyApplicationCall)?.finish()
        }
    }

    override fun start(wait: Boolean): NettyApplicationEngine {
        try {
            channels = bootstraps.zip(configuration.connectors)
                .map { it.first.bind(it.second.host, it.second.port) }
                .map { it.sync().channel() }
            val connectors = channels!!.zip(configuration.connectors)
                .map { it.second.withPort(it.first.localAddress().port) }
            resolvedConnectorsDeferred.complete(connectors)
        } catch (cause: BindException) {
            terminate()
            throw cause
        }

        monitor.raiseCatching(ServerReady, environment, environment.log)

        cancellationJob = stopServerOnCancellation(
            applicationProvider(),
            configuration.shutdownGracePeriod,
            configuration.shutdownTimeout
        )

        if (wait) {
            channels?.map { it.closeFuture() }?.forEach { it.sync() }
            stop(configuration.shutdownGracePeriod, configuration.shutdownTimeout)
        }
        return this
    }

    private fun terminate() {
        connectionEventGroup.shutdownGracefully().sync()
        workerEventGroup.shutdownGracefully().sync()
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        cancellationJob?.complete()
        monitor.raise(ApplicationStopPreparing, environment)
        val channelFutures = channels?.mapNotNull { if (it.isOpen) it.close() else null }.orEmpty()

        try {
            val shutdownConnections =
                connectionEventGroup.shutdownGracefully(gracePeriodMillis, timeoutMillis, TimeUnit.MILLISECONDS)
            shutdownConnections.await()

            val shutdownWorkers =
                workerEventGroup.shutdownGracefully(gracePeriodMillis, timeoutMillis, TimeUnit.MILLISECONDS)
            if (configuration.shareWorkGroup) {
                shutdownWorkers.await()
            } else {
                val shutdownCall =
                    callEventGroup.shutdownGracefully(gracePeriodMillis, timeoutMillis, TimeUnit.MILLISECONDS)
                shutdownWorkers.await()
                shutdownCall.await()
            }
        } finally {
            channelFutures.forEach { it.sync() }
        }
    }

    override fun toString(): String {
        return "Netty($environment)"
    }
}

internal fun getChannelClass(): KClass<out ServerSocketChannel> = when {
    KQueue.isAvailable() -> KQueueServerSocketChannel::class
    Epoll.isAvailable() -> EpollServerSocketChannel::class
    else -> NioServerSocketChannel::class
}
