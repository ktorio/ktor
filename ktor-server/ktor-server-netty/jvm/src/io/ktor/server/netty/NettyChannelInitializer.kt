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
import io.netty.handler.flush.FlushConsolidationHandler
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.SslProvider
import io.netty.handler.ssl.SupportedCipherSuiteFilter
import io.netty.handler.timeout.IdleState
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutException
import io.netty.handler.timeout.WriteTimeoutHandler
import io.netty.util.concurrent.EventExecutor
import io.netty.util.concurrent.EventExecutorGroup
import java.io.FileInputStream
import java.nio.channels.ClosedChannelException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
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
    private val resolveCallExecutor: (ChannelHandlerContext) -> EventExecutor,
    private val engineContext: CoroutineContext,
    private val userContext: CoroutineContext,
    private val connector: EngineConnectorConfig,
    private val runningLimit: Int,
    private val responseWriteTimeout: Int,
    private val requestReadTimeout: Int,
    private val httpServerCodec: () -> HttpServerCodec,
    private val channelPipelineConfig: ChannelPipeline.() -> Unit,
    private val enableHttp2: Boolean,
    private val enableH2c: Boolean,
    private val enableFlushConsolidation: Boolean,
    private val readerIdleTimeout: Int,
    private val writerIdleTimeout: Int,
    private val allIdleTimeout: Int,
) : ChannelInitializer<SocketChannel>() {
    private var sslContext: SslContext? = null

    @Deprecated(
        message = "Use main constructor",
        replaceWith = ReplaceWith(
            "NettyChannelInitializer(" +
                "getRequestBodySizeEstimator, enginePipeline, environment, callEventGroup, " +
                "userContext, engineContext, connector, maxInitialLineLength, maxHeaderSize, " +
                "maxChunkSize, httpServerCodec, channelPipelineConfig, enableHttp2, enableH2c)"
        )
    )
    public constructor(
        applicationProvider: () -> Application,
        enginePipeline: EnginePipeline,
        environment: ApplicationEnvironment,
        callEventGroup: EventExecutorGroup,
        engineContext: CoroutineContext,
        userContext: CoroutineContext,
        connector: EngineConnectorConfig,
        runningLimit: Int,
        responseWriteTimeout: Int,
        requestReadTimeout: Int,
        httpServerCodec: () -> HttpServerCodec,
        channelPipelineConfig: ChannelPipeline.() -> Unit,
        enableHttp2: Boolean,
    ) : this(
        applicationProvider = applicationProvider,
        enginePipeline = enginePipeline,
        environment = environment,
        callEventGroup = callEventGroup,
        engineContext = engineContext,
        userContext = userContext,
        connector = connector,
        runningLimit = runningLimit,
        responseWriteTimeout = responseWriteTimeout,
        requestReadTimeout = requestReadTimeout,
        httpServerCodec = httpServerCodec,
        channelPipelineConfig = channelPipelineConfig,
        enableHttp2 = enableHttp2,
        enableH2c = false
    )

    @Deprecated(
        message = "Use main constructor",
        replaceWith = ReplaceWith(
            "NettyChannelInitializer(" +
                "applicationProvider, enginePipeline, environment, " +
                "callExecutorResolver(callEventGroup, false), engineContext, userContext, connector, " +
                "runningLimit, responseWriteTimeout, requestReadTimeout, httpServerCodec, " +
                "channelPipelineConfig, enableHttp2, enableH2c, false)"
        )
    )
    public constructor(
        applicationProvider: () -> Application,
        enginePipeline: EnginePipeline,
        environment: ApplicationEnvironment,
        callEventGroup: EventExecutorGroup,
        engineContext: CoroutineContext,
        userContext: CoroutineContext,
        connector: EngineConnectorConfig,
        runningLimit: Int,
        responseWriteTimeout: Int,
        requestReadTimeout: Int,
        httpServerCodec: () -> HttpServerCodec,
        channelPipelineConfig: ChannelPipeline.() -> Unit,
        enableHttp2: Boolean,
        enableH2c: Boolean,
    ) : this(
        applicationProvider = applicationProvider,
        enginePipeline = enginePipeline,
        environment = environment,
        resolveCallExecutor = callExecutorResolver(callEventGroup, false),
        engineContext = engineContext,
        userContext = userContext,
        connector = connector,
        runningLimit = runningLimit,
        responseWriteTimeout = responseWriteTimeout,
        requestReadTimeout = requestReadTimeout,
        httpServerCodec = httpServerCodec,
        channelPipelineConfig = channelPipelineConfig,
        enableHttp2 = enableHttp2,
        enableH2c = enableH2c,
        enableFlushConsolidation = false,
        readerIdleTimeout = 0,
        writerIdleTimeout = 0,
        allIdleTimeout = 0,
    )

    @Deprecated(
        message = "Use main constructor",
        replaceWith = ReplaceWith(
            "NettyChannelInitializer(" +
                "applicationProvider, enginePipeline, environment, resolveCallExecutor, engineContext, " +
                "userContext, connector, runningLimit, responseWriteTimeout, requestReadTimeout, " +
                "httpServerCodec, channelPipelineConfig, enableHttp2, enableH2c, enableFlushConsolidation, 0, 0, 0)"
        )
    )
    public constructor(
        applicationProvider: () -> Application,
        enginePipeline: EnginePipeline,
        environment: ApplicationEnvironment,
        resolveCallExecutor: (ChannelHandlerContext) -> EventExecutor,
        engineContext: CoroutineContext,
        userContext: CoroutineContext,
        connector: EngineConnectorConfig,
        runningLimit: Int,
        responseWriteTimeout: Int,
        requestReadTimeout: Int,
        httpServerCodec: () -> HttpServerCodec,
        channelPipelineConfig: ChannelPipeline.() -> Unit,
        enableHttp2: Boolean,
        enableH2c: Boolean,
        enableFlushConsolidation: Boolean,
    ) : this(
        applicationProvider = applicationProvider,
        enginePipeline = enginePipeline,
        environment = environment,
        resolveCallExecutor = resolveCallExecutor,
        engineContext = engineContext,
        userContext = userContext,
        connector = connector,
        runningLimit = runningLimit,
        responseWriteTimeout = responseWriteTimeout,
        requestReadTimeout = requestReadTimeout,
        httpServerCodec = httpServerCodec,
        channelPipelineConfig = channelPipelineConfig,
        enableHttp2 = enableHttp2,
        enableH2c = enableH2c,
        enableFlushConsolidation = enableFlushConsolidation,
        readerIdleTimeout = 0,
        writerIdleTimeout = 0,
        allIdleTimeout = 0,
    )

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
            if (enableFlushConsolidation) {
                addLast(
                    "flushConsolidation",
                    FlushConsolidationHandler(
                        FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES,
                        false
                    )
                )
            }

            when {
                connector is EngineSSLConnectorConfig -> {
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
                }

                enableHttp2 && enableH2c -> {
                    configurePipeline(this, Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME.toString())
                }

                else -> {
                    configurePipeline(this, ApplicationProtocolNames.HTTP_1_1)
                }
            }
        }
    }

    private fun configurePipeline(pipeline: ChannelPipeline, protocol: String) {
        when (protocol) {
            ApplicationProtocolNames.HTTP_2 -> {
                val application = applicationProvider()
                val handler = NettyHttp2Handler(
                    enginePipeline,
                    application,
                    resolveCallExecutor,
                    application.coroutineContext + userContext,
                    runningLimit
                )

                pipeline.addLast(Http2MultiplexCodecBuilder.forServer(handler).build())
                // Swallow connection-level Http2Frame messages (e.g. SETTINGS, PING, GOAWAY) that the
                // Http2MultiplexCodec forwards down the parent pipeline. Without this, those frames
                // would reach Netty's tail handler and trigger "Discarded inbound message" warnings.
                pipeline.addLast(NettyHttp2ConnectionSink)
                pipeline.channel().closeFuture().addListener {
                    handler.onConnectionClose()
                }
                channelPipelineConfig(pipeline)
            }

            Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME.toString() -> {
                val application = applicationProvider()
                val handler = NettyHttp2Handler(
                    enginePipeline,
                    application,
                    resolveCallExecutor,
                    application.coroutineContext + userContext,
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
                // Swallow connection-level Http2Frame messages (e.g. SETTINGS, PING, GOAWAY) once the
                // connection has been upgraded to HTTP/2 and the multiplex codec is forwarding them down
                // the parent pipeline. Without this, those frames would reach Netty's tail handler and
                // trigger "Discarded inbound message" warnings. For requests that remain HTTP/1.1, this
                // sink is a no-op.
                pipeline.addLast(NettyHttp2ConnectionSink)

                // autoRelease = false: channelRead0 always forwards msg via fireChannelRead without
                // retaining it first, so the default auto-release would drop its refCnt a second time.
                pipeline.addLast(object : SimpleChannelInboundHandler<HttpMessage>(false) {
                    @Throws(Exception::class)
                    override fun channelRead0(ctx: ChannelHandlerContext, msg: HttpMessage) {
                        val pipe = ctx.pipeline()

                        val http1handler = NettyHttp1Handler(
                            applicationProvider,
                            enginePipeline,
                            environment,
                            resolveCallExecutor,
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
                        val idleHandler = idleStateHandler(http1handler)
                        if (idleHandler != null) {
                            pipe.addAfter("timeout", "idle", idleHandler)
                            pipe.addAfter("idle", "http1", http1handler)
                        } else {
                            pipe.addAfter("timeout", "http1", http1handler)
                        }
                        pipe.remove(upgradeHandler)
                        pipe.remove(ctx.name())

                        ctx.fireChannelActive()
                        ctx.fireChannelRead(msg)
                    }
                })

                pipeline.channel().closeFuture().addListener {
                    handler.onConnectionClose()
                }
                channelPipelineConfig(pipeline)
            }

            ApplicationProtocolNames.HTTP_1_1 -> {
                val handler = NettyHttp1Handler(
                    applicationProvider,
                    enginePipeline,
                    environment,
                    resolveCallExecutor,
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
                    idleStateHandler(handler)?.let { addLast("idle", it) }
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

    private fun idleStateHandler(http1Handler: NettyHttp1Handler): KtorIdleStateHandler? {
        if (readerIdleTimeout <= 0 && writerIdleTimeout <= 0 && allIdleTimeout <= 0) return null
        return KtorIdleStateHandler(readerIdleTimeout, writerIdleTimeout, allIdleTimeout, http1Handler::hasActiveCalls)
    }

    public companion object {
        internal val alpnProvider by lazy { findAlpnProvider() }

        private fun findAlpnProvider(): SslProvider? {
            try {
                if (SslProvider.isAlpnSupported(SslProvider.OPENSSL)) {
                    return SslProvider.OPENSSL
                }
            } catch (_: Throwable) {
            }

            try {
                if (SslProvider.isAlpnSupported(SslProvider.JDK)) {
                    return SslProvider.JDK
                }
            } catch (_: Throwable) {
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

/**
 * Closes HTTP/1.1 connections that stay idle, using [IdleStateHandler] with `observeOutput = true`
 * so that a write still moving bytes counts as activity. A non-positive timeout disables that check.
 *
 * - Writer idle: fails the channel with [WriteTimeoutException] when response data is pending but no bytes
 *   have been written for [writerIdleTimeoutSeconds]. A slow client that keeps reading is never disconnected.
 * - Reader idle: closes the connection when nothing has been received for [readerIdleTimeoutSeconds],
 *   no call is in progress and no response data is pending, so a long response to a client that sends nothing
 *   is not cut off.
 * - All idle: closes the connection when nothing has been read or written for [allIdleTimeoutSeconds]
 *   and no response data is pending.
 *
 * The first idle event is ignored for writer idle: [IdleStateHandler] raises it without checking output progress,
 * so it would fail a large write that is still moving bytes. Idle events are not passed further down the pipeline.
 */
internal class KtorIdleStateHandler(
    readerIdleTimeoutSeconds: Int,
    writerIdleTimeoutSeconds: Int,
    allIdleTimeoutSeconds: Int,
    private val hasActiveCalls: () -> Boolean,
) : IdleStateHandler(
    true,
    readerIdleTimeoutSeconds.toLong(),
    writerIdleTimeoutSeconds.toLong(),
    allIdleTimeoutSeconds.toLong(),
    TimeUnit.SECONDS
) {

    override fun channelIdle(ctx: ChannelHandlerContext, evt: IdleStateEvent) {
        val pendingBytes = ctx.channel().unsafe().outboundBuffer()?.totalPendingWriteBytes() ?: 0
        when (evt.state()) {
            IdleState.WRITER_IDLE -> if (!evt.isFirst && pendingBytes > 0) {
                ctx.fireExceptionCaught(WriteTimeoutException.INSTANCE)
            }

            // A finished call can still have response bytes in the outbound buffer
            IdleState.READER_IDLE -> if (!hasActiveCalls() && pendingBytes == 0L) ctx.close()

            IdleState.ALL_IDLE -> if (pendingBytes == 0L) ctx.close()

            null -> {}
        }
    }
}
