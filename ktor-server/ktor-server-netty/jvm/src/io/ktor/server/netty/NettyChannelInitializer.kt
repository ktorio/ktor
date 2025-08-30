/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.http1.*
import io.ktor.server.netty.http2.*
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpMessage
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpServerExpectContinueHandler
import io.netty.handler.codec.http.HttpServerUpgradeHandler
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler
import io.netty.handler.codec.http2.Http2CodecUtil
import io.netty.handler.codec.http2.Http2MultiplexCodecBuilder
import io.netty.handler.codec.http2.Http2SecurityUtil
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.SslProvider
import io.netty.handler.ssl.SupportedCipherSuiteFilter
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import io.netty.util.concurrent.EventExecutorGroup
import kotlinx.coroutines.cancel
import java.io.FileInputStream
import java.nio.channels.ClosedChannelException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import kotlin.coroutines.CoroutineContext

/**
 * A [ChannelInitializer] implementation that sets up the default ktor channel pipeline
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.NettyChannelInitializer)
 */
public class NettyChannelInitializer(
    private val applicationProvider: () -> Application,
    private val enginePipeline: EnginePipeline,
    private val environment: ApplicationEnvironment,
    private val callEventGroup: EventExecutorGroup,
    private val engineContext: CoroutineContext,
    private val userContext: CoroutineContext,
    private val connector: EngineConnectorConfig,
    private val runningLimit: Int,
    private val responseWriteTimeout: Int,
    private val requestReadTimeout: Int,
    private val httpServerCodec: () -> HttpServerCodec,
    private val channelPipelineConfig: ChannelPipeline.() -> Unit,
    private val enableHttp2: Boolean,
    private val enableH2c: Boolean = false
) : ChannelInitializer<SocketChannel>() {
    private var sslContext: SslContext? = null

    init {
        if (connector is EngineSSLConnectorConfig) {

            // It is better but netty-openssl doesn't support it
//              val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
//              kmf.init(ktorConnector.keyStore, password)
//              password.fill('\u0000')

            @Suppress("UNCHECKED_CAST")
            val chain1 = connector.keyStore.getCertificateChain(connector.keyAlias).toList() as List<X509Certificate>
            val certs = chain1.toList().toTypedArray()
            val password = connector.privateKeyPassword()
            val pk = connector.keyStore.getKey(connector.keyAlias, password) as PrivateKey
            password.fill('\u0000')

            sslContext = SslContextBuilder.forServer(pk, *certs)
                .apply {
                    if (enableHttp2 && alpnProvider != null) {
                        sslProvider(alpnProvider)
                        ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                        applicationProtocolConfig(
                            ApplicationProtocolConfig(
                                ApplicationProtocolConfig.Protocol.ALPN,
                                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                ApplicationProtocolNames.HTTP_2,
                                ApplicationProtocolNames.HTTP_1_1
                            )
                        )
                    }
                    connector.trustManagerFactory()?.let { this.trustManager(it) }
                }
                .build()
        }
    }

    override fun initChannel(ch: SocketChannel) {
        with(ch.pipeline()) {
            if (enableHttp2 && enableH2c) {
                configurePipeline(this, Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME.toString())

                return
            }

            if (connector is EngineSSLConnectorConfig) {
                val sslEngine = sslContext!!.newEngine(ch.alloc()).apply {
                    if (connector.hasTrustStore()) {
                        useClientMode = false
                        needClientAuth = true
                    }
                    connector.enabledProtocols?.let {
                        enabledProtocols = it.toTypedArray()
                    }
                }
                addLast("ssl", SslHandler(sslEngine))

                if (enableHttp2 && alpnProvider != null) {
                    addLast(NegotiatedPipelineInitializer())
                } else {
                    configurePipeline(this, ApplicationProtocolNames.HTTP_1_1)
                }
            } else {
                configurePipeline(this, ApplicationProtocolNames.HTTP_1_1)
            }
        }
    }

    private fun configurePipeline(pipeline: ChannelPipeline, protocol: String) {
        when (protocol) {
            ApplicationProtocolNames.HTTP_2 -> {
                val handler = NettyHttp2Handler(
                    enginePipeline,
                    applicationProvider(),
                    callEventGroup,
                    userContext,
                    runningLimit
                )

                pipeline.addLast(Http2MultiplexCodecBuilder.forServer(handler).build())
                pipeline.channel().closeFuture().addListener {
                    handler.cancel()
                }
                channelPipelineConfig(pipeline)
            }

            Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME.toString() -> {
                val handler = NettyHttp2Handler(
                    enginePipeline,
                    applicationProvider(),
                    callEventGroup,
                    userContext,
                    runningLimit
                )

                val multiplexHandler = Http2MultiplexCodecBuilder.forServer(handler).build()

                val codec = httpServerCodec()

                val upgradeHandler = HttpServerUpgradeHandler(codec) {
                    Http2ServerUpgradeCodec(multiplexHandler)
                }

                val cleartextHttp2ServerUpgradeHandler = CleartextHttp2ServerUpgradeHandler(
                    codec,
                    upgradeHandler,
                    multiplexHandler
                )

                pipeline.addLast("cleartextUpgradeHandler", cleartextHttp2ServerUpgradeHandler)

                pipeline.addLast(object : SimpleChannelInboundHandler<HttpMessage>() {
                    @Throws(Exception::class)
                    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpMessage) {
                        val pipe = ctx.pipeline()

                        val http1handler = NettyHttp1Handler(
                            applicationProvider,
                            enginePipeline,
                            environment,
                            callEventGroup,
                            engineContext,
                            userContext,
                            runningLimit
                        )

                        if (requestReadTimeout > 0) {
                            pipe.addAfter(ctx.name(), "readTimeout", KtorReadTimeoutHandler(requestReadTimeout))
                            pipe.addAfter("readTimeout", "continue", HttpServerExpectContinueHandler())
                        } else {
                            pipe.addAfter(ctx.name(), "continue", HttpServerExpectContinueHandler())
                        }
                        pipe.addAfter("continue", "timeout", WriteTimeoutHandler(responseWriteTimeout))
                        pipe.addAfter("timeout", "http1", http1handler)

                        pipe.remove(upgradeHandler)
                        pipe.remove(ctx.name())

                        ctx.fireChannelActive()
                        ctx.fireChannelRead(msg)
                    }
                })

                pipeline.channel().closeFuture().addListener {
                    handler.cancel()
                }
                channelPipelineConfig(pipeline)
            }

            ApplicationProtocolNames.HTTP_1_1 -> {
                val handler = NettyHttp1Handler(
                    applicationProvider,
                    enginePipeline,
                    environment,
                    callEventGroup,
                    engineContext,
                    userContext,
                    runningLimit
                )

                with(pipeline) {
                    //                    addLast(LoggingHandler(LogLevel.WARN))
                    if (requestReadTimeout > 0) {
                        addLast("readTimeout", KtorReadTimeoutHandler(requestReadTimeout))
                    }
                    addLast("codec", httpServerCodec())
                    addLast("continue", HttpServerExpectContinueHandler())
                    addLast("timeout", WriteTimeoutHandler(responseWriteTimeout))
                    addLast("http1", handler)
                    channelPipelineConfig()
                }

                pipeline.context("codec").fireChannelActive()
            }

            else -> {
                environment.log.error("Unsupported protocol $protocol")
                pipeline.close()
            }
        }
    }

    private fun EngineSSLConnectorConfig.hasTrustStore() = trustStore != null || trustStorePath != null

    private fun EngineSSLConnectorConfig.trustManagerFactory(): TrustManagerFactory? {
        val trustStore = trustStore ?: trustStorePath?.let { file ->
            FileInputStream(file).use { fis ->
                KeyStore.getInstance(KeyStore.getDefaultType()).also { it.load(fis, null) }
            }
        }
        return trustStore?.let { store ->
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).also { it.init(store) }
        }
    }

    private inner class NegotiatedPipelineInitializer :
        ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
        override fun configurePipeline(ctx: ChannelHandlerContext, protocol: String) =
            configurePipeline(ctx.pipeline(), protocol)

        override fun handshakeFailure(ctx: ChannelHandlerContext, cause: Throwable?) {
            if (cause is ClosedChannelException) {
                // connection closed during TLS handshake: there is no need to log it
                ctx.close()
            } else {
                super.handshakeFailure(ctx, cause)
            }
        }
    }

    public companion object {
        internal val alpnProvider by lazy { findAlpnProvider() }

        private fun findAlpnProvider(): SslProvider? {
            try {
                if (SslProvider.isAlpnSupported(SslProvider.OPENSSL)) {
                    return SslProvider.OPENSSL
                }
            } catch (ignore: Throwable) {
            }

            try {
                if (SslProvider.isAlpnSupported(SslProvider.JDK)) {
                    return SslProvider.JDK
                }
            } catch (ignore: Throwable) {
            }

            return null
        }
    }
}

internal class KtorReadTimeoutHandler(requestReadTimeout: Int) : ReadTimeoutHandler(requestReadTimeout) {
    private var closed = false

    override fun readTimedOut(ctx: ChannelHandlerContext?) {
        if (!closed) {
            ctx?.fireExceptionCaught(ReadTimeoutException.INSTANCE)
            closed = true
        }
    }
}
