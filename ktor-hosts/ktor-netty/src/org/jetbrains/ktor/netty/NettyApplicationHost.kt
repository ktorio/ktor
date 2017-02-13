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
import org.jetbrains.ktor.netty.http2.*
import org.jetbrains.ktor.transform.*
import java.security.*
import java.security.cert.*
import java.util.concurrent.*
import java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory

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

    private val callThreadPool = ForkJoinPool(parallelism, defaultForkJoinWorkerThreadFactory, null, true)
    internal val callDispatcher = callThreadPool.toCoroutineDispatcher() // executes call handlers


    private val bootstraps = hostConfig.connectors.map { ktorConnector ->
        ServerBootstrap().apply {
            group(connectionEventGroup, workerEventGroup)
            channel(NioServerSocketChannel::class.java)
            childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    with(ch.pipeline()) {
                        if (ktorConnector is HostSSLConnectorConfig) {
//                            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
//                            kmf.init(ktorConnector.keyStore, password)
//                            password.fill('\u0000')

                            val chain1 = ktorConnector.keyStore.getCertificateChain(ktorConnector.keyAlias).toList() as List<X509Certificate>
                            val certs = chain1.toList().toTypedArray<X509Certificate>()
                            val password = ktorConnector.privateKeyPassword()
                            val pk = ktorConnector.keyStore.getKey(ktorConnector.keyAlias, password) as PrivateKey
                            password.fill('\u0000')

                            addLast("ssl", SslContextBuilder.forServer(pk, *certs)
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
                            addLast(Initializer())
                        } else {
                            configurePipeline(this, ApplicationProtocolNames.HTTP_1_1)
                        }
                    }
                }
            })
        }
    }

    private val hostPipeline = defaultHostPipeline(environment)

    init {
        applicationLifecycle.onBeforeInitializeApplication {
            install(TransformationSupport).registerDefaultHandlers()
        }
    }

    override fun start(wait: Boolean) {
        applicationLifecycle.ensureApplication()
        environment.log.trace("Starting server...")
        val channelFutures = bootstraps.zip(hostConfig.connectors).map { it.first.bind(it.second.host, it.second.port) }
        environment.log.trace("Server running.")

        if (wait) {
            channelFutures.map { it.channel().closeFuture() }.forEach { it.sync() }
            applicationLifecycle.dispose()
            environment.log.trace("Server stopped.")
        }
    }

    override fun stop() {
        workerEventGroup.shutdownGracefully()
        connectionEventGroup.shutdownGracefully()
        callThreadPool.shutdown()

        applicationLifecycle.dispose()
        environment.log.trace("Server stopped.")
    }

    fun configurePipeline(pipeline: ChannelPipeline, protocol: String) {
        when (protocol) {
            ApplicationProtocolNames.HTTP_2 -> {
                val connection = DefaultHttp2Connection(true)
                val writer = DefaultHttp2FrameWriter()
                val reader = DefaultHttp2FrameReader(false)

                val encoder = DefaultHttp2ConnectionEncoder(connection, writer)
                val decoder = DefaultHttp2ConnectionDecoder(connection, encoder, reader)

                pipeline.addLast(HostHttp2Handler(encoder, decoder, Http2Settings()))
                pipeline.addLast(Multiplexer(pipeline.channel(), NettyHostHttp2Handler(this, connection, hostPipeline)))
            }
            ApplicationProtocolNames.HTTP_1_1 -> {
                with(pipeline) {
                    //addLast(LoggingHandler())
                    addLast(HttpServerCodec())
                    addLast(ChunkedWriteHandler())
                    addLast(WriteTimeoutHandler(10))
                    addLast(NettyHostHttp1Handler(this@NettyApplicationHost, hostPipeline))
                }
            }
            else -> {
                application.environment.log.error("Unsupported protocol $protocol")
                pipeline.close()
            }
        }
    }

    inner class Initializer : ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
        override fun configurePipeline(ctx: ChannelHandlerContext, protocol: String) = this@NettyApplicationHost.configurePipeline(ctx.pipeline(), protocol)
    }

    companion object {
        val alpnProvider by lazy { findAlpnProvider() }

        fun findAlpnProvider(): SslProvider? {
            try {
                Class.forName("sun.security.ssl.ALPNExtension", true, null)
                return SslProvider.JDK
            } catch (ignore: Throwable) {
            }

            try {
                if (OpenSsl.isAlpnSupported()) {
                    return SslProvider.OPENSSL
                }
            } catch (ignore: Throwable) {
            }

            return null
        }
    }

}


class NettyConnectionPool(parallelism: Int) : NioEventLoopGroup(parallelism)
class NettyWorkerPool(parallelism: Int) : NioEventLoopGroup(parallelism)