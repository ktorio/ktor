/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty

import io.ktor.server.engine.*
import io.ktor.server.netty.cio.*
import io.ktor.server.netty.http1.*
import io.ktor.server.netty.http2.*
import io.netty.channel.*
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import io.netty.handler.ssl.*
import io.netty.handler.timeout.*
import io.netty.util.concurrent.*
import kotlinx.coroutines.*
import java.nio.channels.*
import java.security.*
import java.security.cert.*
import kotlin.coroutines.*

/**
 * A [ChannelInitializer] implementation that does setup the default ktor channel pipeline
 */
@EngineAPI
public class NettyChannelInitializer(
    private val enginePipeline: EnginePipeline,
    private val environment: ApplicationEngineEnvironment,
    private val callEventGroup: EventExecutorGroup,
    private val engineContext: CoroutineContext,
    private val userContext: CoroutineContext,
    private val connector: EngineConnectorConfig,
    private val requestQueueLimit: Int,
    private val runningLimit: Int,
    private val responseWriteTimeout: Int,
    private val requestReadTimeout: Int,
    private val httpServerCodec: () -> HttpServerCodec
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

            sslContext = SslContextBuilder.forServer(pk, *certs).apply {
                if (alpnProvider != null) {
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
            }
                .build()
        }
    }

    @Suppress("KDocMissingDocumentation")
    override fun initChannel(ch: SocketChannel) {
        with(ch.pipeline()) {
            if (connector is EngineSSLConnectorConfig) {
                addLast("ssl", sslContext!!.newHandler(ch.alloc()))

                if (alpnProvider != null) {
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
                val handler = NettyHttp2Handler(enginePipeline, environment.application, callEventGroup, userContext)
                pipeline.addLast(Http2MultiplexCodecBuilder.forServer(handler).build())
                pipeline.channel().closeFuture().addListener {
                    handler.cancel()
                }
            }
            ApplicationProtocolNames.HTTP_1_1 -> {
                val requestQueue = NettyRequestQueue(requestQueueLimit, runningLimit)
                val handler = NettyHttp1Handler(
                    enginePipeline,
                    environment,
                    callEventGroup,
                    engineContext,
                    userContext,
                    requestQueue
                )

                with(pipeline) {
                    //                    addLast(LoggingHandler(LogLevel.WARN))
                    if (requestReadTimeout > 0) {
                        addLast("readTimeout", ReadTimeoutHandler(requestReadTimeout))
                    }
                    addLast("codec", httpServerCodec())
                    addLast("continue", HttpServerExpectContinueHandler())
                    addLast("timeout", WriteTimeoutHandler(responseWriteTimeout))
                    addLast("http1", handler)
                }

                pipeline.context("codec").fireChannelActive()
            }
            else -> {
                environment.log.error("Unsupported protocol $protocol")
                pipeline.close()
            }
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

    @EngineAPI
    public companion object {
        internal val alpnProvider by lazy { findAlpnProvider() }

        private fun findAlpnProvider(): SslProvider? {
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
