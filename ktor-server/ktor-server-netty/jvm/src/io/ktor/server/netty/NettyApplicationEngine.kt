/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.http3.*
import io.ktor.util.network.*
import io.ktor.util.pipeline.*
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoopGroup
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollDatagramChannel
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueDatagramChannel
import io.netty.channel.kqueue.KQueueServerSocketChannel
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectDecoder
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.quic.QuicSslContext
import io.netty.handler.codec.quic.QuicSslContextBuilder
import io.netty.handler.codec.quic.QuicTokenHandler
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.net.BindException
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.system.measureTimeMillis

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
         * If set to `true` and [enableHttp2] is set to `true`, enables HTTP/2 protocol without TLS for Netty engine
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyApplicationEngine.Configuration.enableH2c)
         */
        public var enableH2c: Boolean = false

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
         * If set to `true`, enables HTTP/3 protocol (over QUIC/UDP) for Netty engine.
         * Requires an SSL connector to be configured (HTTP/3 always uses TLS).
         * The HTTP/3 endpoint will listen on the same port as the SSL connector but over UDP.
         */
        public var enableHttp3: Boolean = false

        /**
         * The [QuicTokenHandler] used to generate and validate QUIC retry tokens
         * when HTTP/3 is enabled. By default, a secure HMAC-SHA256-based handler
         * is used that cryptographically signs tokens with a randomly generated key
         * and rejects forged or expired tokens.
         *
         * Callers may replace this with a custom [QuicTokenHandler] implementation
         * to use a different signing strategy or integrate with external token services.
         *
         * Only takes effect when [enableHttp3] is `true`.
         */
        public var quicTokenHandler: QuicTokenHandler = HmacQuicTokenHandler()

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

    private val workerDispatcher by lazy {
        workerEventGroup.asCoroutineDispatcher()
    }

    private var cancellationJob: CompletableJob? = null

    private var channels: List<Channel>? = null
    private var http3Channels: List<Channel>? = null
    internal val bootstraps: List<ServerBootstrap> by lazy {
        configuration.connectors.map(::createBootstrap)
    }
    private val http3Bootstraps: List<Bootstrap> by lazy {
        if (!configuration.enableHttp3) return@lazy emptyList()
        configuration.connectors
            .filterIsInstance<EngineSSLConnectorConfig>()
            .map { createHttp3Bootstrap(it) }
    }

    private fun createBootstrap(connector: EngineConnectorConfig): ServerBootstrap {
        return customBootstrap.clone().apply {
            if (config().group() == null && config().childGroup() == null) {
                group(connectionEventGroup, workerEventGroup)
            }

            if (config().channelFactory() == null) {
                channel(getChannelClass().java)
            }

            val userContext =
                NettyApplicationCallHandler.CallHandlerCoroutineName +
                    NettyDispatcher +
                    DefaultUncaughtExceptionHandler(environment.log)

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
                    configuration.enableHttp2,
                    configuration.enableH2c
                )
            )
            if (configuration.tcpKeepAlive) {
                childOption(ChannelOption.SO_KEEPALIVE, true)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createHttp3Bootstrap(connector: EngineSSLConnectorConfig): Bootstrap {
        val chain = connector.keyStore.getCertificateChain(connector.keyAlias).toList() as List<X509Certificate>
        val certs = chain.toTypedArray()
        val password = connector.privateKeyPassword()
        val pk = connector.keyStore.getKey(connector.keyAlias, password) as PrivateKey
        password.fill('\u0000')

        val quicSslContext: QuicSslContext = QuicSslContextBuilder.forServer(pk, null, *certs)
            .applicationProtocols(*io.netty.handler.codec.http3.Http3.supportedApplicationProtocols())
            .build()

        val userContext =
            NettyApplicationCallHandler.CallHandlerCoroutineName +
                NettyDispatcher +
                DefaultUncaughtExceptionHandler(environment.log)

        return Bootstrap().apply {
            group(workerEventGroup)
            channel(getDatagramChannelClass().java)
            handler(
                NettyHttp3ChannelInitializer(
                    applicationProvider,
                    pipeline,
                    callEventGroup,
                    userContext,
                    configuration.runningLimit,
                    quicSslContext,
                    configuration.quicTokenHandler
                )
            )
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

            // Bind HTTP/3 (QUIC/UDP) on the same resolved port as the TCP SSL connector.
            // TCP and UDP can share the same port number since they are different protocols.
            val resolvedSslConnectors = channels!!.zip(configuration.connectors)
                .filter { it.second is EngineSSLConnectorConfig }
                .map { it.second.host to (it.first.localAddress() as java.net.InetSocketAddress).port }
            http3Channels = http3Bootstraps.zip(resolvedSslConnectors)
                .map { (bootstrap, hostPort) -> bootstrap.bind(hostPort.first, hostPort.second) }
                .map { it.sync().channel() }

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
            val allChannels = (channels.orEmpty() + http3Channels.orEmpty())
            allChannels.map { it.closeFuture() }.forEach { it.sync() }
            stop(configuration.shutdownGracePeriod, configuration.shutdownTimeout)
        }
        return this
    }

    private fun terminate() {
        withStopException {
            connectionEventGroup.shutdownGracefully().sync()
        }
        withStopException {
            callEventGroup.shutdownGracefully().sync()
        }
    }

    private inline fun <R> withStopException(crossinline block: () -> R) {
        runCatching(block).onFailure {
            environment.log.error("Exception thrown during engine stop", it)
        }
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        cancellationJob?.complete()
        monitor.raise(ApplicationStopPreparing, environment)

        val channelsCloseTime = measureTimeMillis {
            val allChannels = (channels.orEmpty() + http3Channels.orEmpty())
            val channelFutures = allChannels.mapNotNull { if (it.isOpen) it.close() else null }
            channelFutures.forEach { future ->
                withStopException { future.sync() }
            }
        }

        // Quiet period in Ktor Server and Netty EventLoopGroup are different.
        // Ktor Server waits for all requests to finish without accepting new ones.
        // Netty's EventLoopGroup accepts new tasks during the gracePeriod
        // and always waits at least gracePeriod, even if there are no tasks to complete.
        val noQuietPeriod = 0L
        val timeoutMillis = (timeoutMillis - channelsCloseTime).coerceAtLeast(gracePeriodMillis)
        val shutdownConnections = connectionEventGroup.shutdownGracefully(
            noQuietPeriod,
            timeoutMillis,
            TimeUnit.MILLISECONDS
        )
        val shutdownWorkers = workerEventGroup.shutdownGracefully(
            gracePeriodMillis,
            timeoutMillis,
            TimeUnit.MILLISECONDS
        )
        val workersShutdownTime = measureTimeMillis {
            withStopException { shutdownConnections.sync() }
            withStopException { shutdownWorkers.sync() }
        }
        if (!configuration.shareWorkGroup) {
            withStopException {
                // There should be no new tasks to be scheduled at this point; no quiet period is needed.
                val timeoutMillis = (timeoutMillis - workersShutdownTime).coerceAtLeast(100L)
                callEventGroup.shutdownGracefully(noQuietPeriod, timeoutMillis, TimeUnit.MILLISECONDS).sync()
            }
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

internal fun getDatagramChannelClass(): KClass<out DatagramChannel> = when {
    KQueue.isAvailable() -> KQueueDatagramChannel::class
    Epoll.isAvailable() -> EpollDatagramChannel::class
    else -> NioDatagramChannel::class
}
