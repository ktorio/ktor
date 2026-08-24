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
import io.ktor.utils.io.ExperimentalKtorApi
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
import io.netty.channel.socket.nio.NioChannelOption
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.unix.UnixChannelOption
import io.netty.handler.codec.http.HttpObjectDecoder
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.quic.QuicSslContext
import io.netty.handler.codec.quic.QuicSslContextBuilder
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.net.BindException
import java.net.SocketOption
import java.net.StandardSocketOptions
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
         * Holds the HTTP/3 configuration when HTTP/3 is enabled, or `null` when disabled.
         *
         * Configured via [enableHttp3].
         */
        internal var http3Configuration: NettyHttp3Configuration? = null
            private set

        /**
         * Enables the HTTP/3 protocol (over QUIC/UDP) for the Netty engine.
         *
         * Requires an SSL connector to be configured (HTTP/3 always uses TLS).
         * The HTTP/3 endpoint will listen on the same port as the SSL connector but over UDP.
         *
         * QUIC- and HTTP/3-specific options can be customized through the [configure] lambda,
         * which receives a [NettyHttp3Configuration] as its receiver. These options apply only
         * to the HTTP/3 transport and have no effect on HTTP/1.1 or HTTP/2.
         *
         * Calling this function multiple times replaces the previous configuration.
         */
        @ExperimentalKtorApi
        public fun enableHttp3(configure: NettyHttp3Configuration.() -> Unit = {}) {
            http3Configuration = NettyHttp3Configuration().apply(configure)
        }

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
        val http3Configuration = configuration.http3Configuration ?: return@lazy emptyList()
        require(configuration.connectors.any { it is EngineSSLConnectorConfig }) {
            "Netty HTTP/3 requires at least one SSL connector. Add an SSL connector or disable enableHttp3."
        }
        configuration.connectors
            .filterIsInstance<EngineSSLConnectorConfig>()
            .map { createHttp3Bootstrap(it, http3Configuration) }
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

    /**
     * The `SO_REUSEPORT` [ChannelOption] matching the datagram transport selected by
     * [getDatagramChannelClass], or `null` when unsupported: native transports use
     * [UnixChannelOption.SO_REUSEPORT], while NIO requires the JDK socket option
     * `StandardSocketOptions.SO_REUSEPORT`, which is resolved reflectively because it is only
     * available since Java 9 while this module compiles against the Java 8 API. The field's mere
     * presence only proves the JDK version supports the constant, not that the platform's NIO
     * provider actually implements it (for example, Windows exposes the field but its datagram
     * channels reject the option) — an actual NIO `DatagramChannel`'s `supportedOptions()` is
     * probed to confirm real support before the option is used.
     */
    private val reusePortOption: ChannelOption<Boolean>? get() = reusePortResolution.option

    /**
     * Explains why [reusePortOption] is `null`, distinguishing "this JDK doesn't have the
     * `SO_REUSEPORT` constant" (needs Java 9+) from "this JDK has it, but the platform's NIO
     * provider rejects it anyway" (for example, Windows) — the two require different advice.
     */
    private val reusePortResolution: ReusePortResolution by lazy {
        if (KQueue.isAvailable() || Epoll.isAvailable()) {
            return@lazy ReusePortResolution(UnixChannelOption.SO_REUSEPORT, null)
        }
        try {
            @Suppress("UNCHECKED_CAST")
            val soReusePort = StandardSocketOptions::class.java.getField("SO_REUSEPORT")
                .get(null) as SocketOption<Boolean>
            val supported = java.nio.channels.DatagramChannel.open().use { channel ->
                channel.supportedOptions().contains(soReusePort)
            }
            if (!supported) {
                return@lazy ReusePortResolution(
                    null,
                    "the current platform's NIO datagram provider does not support SO_REUSEPORT"
                )
            }
            ReusePortResolution(NioChannelOption.of(soReusePort), null)
        } catch (_: ReflectiveOperationException) {
            ReusePortResolution(null, "SO_REUSEPORT requires running on Java 9 or newer")
        } catch (_: java.io.IOException) {
            ReusePortResolution(null, "SO_REUSEPORT support could not be determined")
        }
    }

    private class ReusePortResolution(val option: ChannelOption<Boolean>?, val unsupportedReason: String?)

    /**
     * The number of UDP sockets bound per HTTP/3 connector.
     *
     * Every QUIC channel (and all its streams) is served by the event loop of the datagram socket
     * that received it, so a single socket pins the entire HTTP/3 endpoint to one thread. Binding
     * multiple sockets with `SO_REUSEPORT` lets the kernel spread connections across event loops.
     * Kernel-side UDP load balancing across `SO_REUSEPORT` sockets is a Linux kernel feature
     * (available with both epoll and NIO transports), so the automatic default stays at 1 elsewhere.
     */
    private val http3SocketCount: Int by lazy {
        val configured = configuration.http3Configuration?.udpSocketCount
        when {
            configured != null -> {
                check(configured == 1 || reusePortOption != null) {
                    "udpSocketCount = $configured requires SO_REUSEPORT support, but " +
                        "${reusePortResolution.unsupportedReason}. " +
                        "Use a native transport (epoll/kqueue) or set udpSocketCount = 1."
                }
                configured
            }

            isLinux && reusePortOption != null && configuration.workerGroupSize > 1 -> configuration.workerGroupSize

            else -> 1
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createHttp3Bootstrap(
        connector: EngineSSLConnectorConfig,
        http3Configuration: NettyHttp3Configuration
    ): Bootstrap {
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
            if (http3SocketCount > 1) {
                // Non-null is guaranteed by the http3SocketCount initializer check.
                option(checkNotNull(reusePortOption), true)
            }
            if (http3Configuration.udpReceiveBufferSize > 0) {
                option(ChannelOption.SO_RCVBUF, http3Configuration.udpReceiveBufferSize)
            }
            if (http3Configuration.udpSendBufferSize > 0) {
                option(ChannelOption.SO_SNDBUF, http3Configuration.udpSendBufferSize)
            }
            handler(
                NettyHttp3ChannelInitializer(
                    applicationProvider,
                    pipeline,
                    userContext,
                    callEventGroup,
                    configuration.runningLimit,
                    quicSslContext,
                    http3Configuration,
                    useCodecDispatcher = http3SocketCount > 1
                )
            )
        }
    }

    init {
        pipeline.insertPhaseAfter(EnginePipeline.Call, AFTER_CALL_PHASE)
        pipeline.intercept(AFTER_CALL_PHASE) {
            // [NettyApplicationCall.finish] is non-suspending: it only ensures the response is
            // committed (headers + status flushed). The actual write completion is awaited via
            // structured concurrency — the call's responseWriteJob is a child of the call's
            // coroutine Job, so the call coroutine remains "completing" until the I/O-thread
            // writer finishes and cleanup runs from responseWriteJob's invokeOnCompletion handler.
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
            // Multiple sockets per connector (SO_REUSEPORT) spread QUIC connections across
            // event loops; see [http3SocketCount].
            val resolvedSslConnectors = channels!!.zip(configuration.connectors)
                .filter { it.second is EngineSSLConnectorConfig }
                .map { it.second.host to (it.first.localAddress() as java.net.InetSocketAddress).port }
            http3Channels = http3Bootstraps.zip(resolvedSslConnectors)
                .flatMap { (bootstrap, hostPort) ->
                    List(http3SocketCount) { bootstrap.bind(hostPort.first, hostPort.second) }
                }
                .map { it.sync().channel() }

            resolvedConnectorsDeferred.complete(connectors)
        } catch (cause: Throwable) {
            terminate()
            throw cause
        } catch (cause: Throwable) {
            stop(0, 0)
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

        var remainingTimeoutMillis = (timeoutMillis - channelsCloseTime).coerceAtLeast(100L)

        val connectionsShutdownTime = measureTimeMillis {
            withStopException {
                connectionEventGroup.shutdownGracefully(
                    noQuietPeriod,
                    remainingTimeoutMillis,
                    TimeUnit.MILLISECONDS
                ).sync()
            }
        }

        remainingTimeoutMillis = (remainingTimeoutMillis - connectionsShutdownTime).coerceAtLeast(100L)

        val workersShutdownTime = measureTimeMillis {
            withStopException {
                workerEventGroup.shutdownGracefully(
                    gracePeriodMillis.coerceAtMost(remainingTimeoutMillis),
                    remainingTimeoutMillis,
                    TimeUnit.MILLISECONDS
                ).sync()
            }
        }

        if (!configuration.shareWorkGroup) {
            withStopException {
                // There should be no new tasks to be scheduled at this point; no quiet period is needed.
                remainingTimeoutMillis = (remainingTimeoutMillis - workersShutdownTime).coerceAtLeast(100L)
                callEventGroup.shutdownGracefully(noQuietPeriod, remainingTimeoutMillis, TimeUnit.MILLISECONDS).sync()
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

private val isLinux: Boolean = System.getProperty("os.name", "").contains("linux", ignoreCase = true)
