package org.jetbrains.ktor.netty

import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import io.netty.handler.ssl.*
import io.netty.handler.stream.*
import io.netty.handler.timeout.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.transform.*
import java.util.concurrent.*
import java.util.concurrent.ForkJoinPool.*
import javax.net.ssl.*

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
            childHandler(NettyHostChannelInitializer(this@NettyApplicationHost, connector))
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
            stop()
        }
    }

    override fun stop() {
        environment.log.trace("Stopping server…")
        val shutdownConnections = connectionEventGroup.shutdownGracefully(200, 5000, TimeUnit.MILLISECONDS)
        val shutdownWorkers = workerEventGroup.shutdownGracefully(200, 5000, TimeUnit.MILLISECONDS)
        callThreadPool.shutdown()
        shutdownConnections.await()
        shutdownWorkers.await()

        applicationLifecycle.dispose()
        environment.log.trace("Server stopped.")
    }

}

class NettyHostChannelInitializer(val host: NettyApplicationHost, val connector: HostConnectorConfig) : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        val pipeline = ch.pipeline()
        if (connector is HostSSLConnectorConfig) {
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            val password = connector.privateKeyPassword()
            kmf.init(connector.keyStore, password)
            password.fill('\u0000')

            pipeline.addLast("ssl", SslContextBuilder.forServer(kmf)
                    .apply {
                        if (alpnProvider != null) {
                            sslProvider(alpnProvider)
                            ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                            applicationProtocolConfig(ApplicationProtocolConfig(
                                    ApplicationProtocolConfig.Protocol.ALPN,
                                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                    ApplicationProtocolNames.HTTP_2,
                                    ApplicationProtocolNames.HTTP_1_1
                            ))
                        }
                    }
                    .build()
                    .newHandler(ch.alloc()))
        }

        if (alpnProvider != null) {
            pipeline.addLast(Initializer())
        } else {
            configurePipeline(pipeline, ApplicationProtocolNames.HTTP_1_1)
        }
    }

    fun configurePipeline(pipeline: ChannelPipeline, protocol: String) {
        when (protocol) {
            ApplicationProtocolNames.HTTP_2 -> {
                val connection = DefaultHttp2Connection(true)
                val writer = DefaultHttp2FrameWriter()
                val reader = DefaultHttp2FrameReader(false)

                val encoder = DefaultHttp2ConnectionEncoder(connection, writer)
                val decoder = DefaultHttp2ConnectionDecoder(connection, encoder, reader)

/*
                pipeline.addLast(HostHttp2Handler(encoder, decoder, Http2Settings()))
                pipeline.addLast(Multiplexer(pipeline.channel(), HostHttpHandler(this@NettyApplicationHost, connection, byteBufferPool, hostPipeline)))
*/
            }
            ApplicationProtocolNames.HTTP_1_1 -> {
                with(pipeline) {
                    //addLast(LoggingHandler())
                    addLast(HttpServerCodec())
                    addLast(ChunkedWriteHandler())
                    addLast(WriteTimeoutHandler(10))
                    addLast(NettyHostHttp1Handler(host))
                }
            }
            else -> {
                host.application.environment.log.error("Unsupported protocol $protocol")
                pipeline.close()
            }
        }
    }

    inner class Initializer : ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
        override fun configurePipeline(ctx: ChannelHandlerContext, protocol: String) = configurePipeline(ctx.pipeline(), protocol)
    }

    companion object {
        val alpnProvider by lazy { findAlpnProvider() }

        fun findAlpnProvider(): SslProvider? {
            val jettyAlpn = try {
                Class.forName("sun.security.ssl.ALPNExtension", true, null)
                true
            } catch (t: Throwable) {
                false
            }

            return when {
                jettyAlpn -> SslProvider.JDK
                else -> null
            }
        }
    }
}


class NettyConnectionPool(parallelism: Int) : NioEventLoopGroup(parallelism)
class NettyWorkerPool(parallelism: Int) : NioEventLoopGroup(parallelism)