/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.netty

import io.ktor.util.date.*
import io.netty.channel.*
import io.netty.channel.socket.*
import io.netty.handler.codec.http.*
import io.netty.handler.ssl.*
import kotlin.coroutines.*

/**
 * Channel initializer for Netty HTTP client.
 * Configures the pipeline with SSL/TLS, HTTP codec, and response handler.
 *
 * @param sslCtx SSL context for secure connections, null for plain HTTP
 * @param host Target host (used for SNI)
 * @param port Target port
 * @param callContext Coroutine context for the request
 * @param requestData Request data from Ktor
 * @param requestTime Request timestamp
 * @param useHttp2 Whether HTTP/2 is requested (currently falls back to HTTP/1.1)
 */
internal class NettyClientInitializer(
    private val sslCtx: SslContext?,
    private val host: String,
    private val port: Int,
    private val callContext: CoroutineContext,
    private val requestTime: GMTDate,
    private val useHttp2: Boolean = false
) : ChannelInitializer<SocketChannel>() {

    override fun initChannel(ch: SocketChannel) {
        val pipeline = ch.pipeline()

        if (sslCtx != null) {
            configureSslHandler(pipeline)

            // TODO: Implement HTTP/2 support
            // When useHttp2 is true and ALPN negotiates HTTP/2, we should configure
            // an HTTP/2 pipeline. Currently, we always fall back to HTTP/1.1.
            // See HTTP2_INVESTIGATION.md for details on the implementation challenge.
            if (useHttp2) {
                // ALPN is configured in SslContext (NettyHttpEngine.kt:117-129)
                // Future: Add ApplicationProtocolNegotiationHandler here
                // For now, fall back to HTTP/1.1
                configureHttp1Pipeline(pipeline)
            } else {
                configureHttp1Pipeline(pipeline)
            }
        } else {
            // Plain HTTP
            configureHttp1Pipeline(pipeline)
        }
    }

    /**
     * Configures SSL/TLS handler with hostname verification.
     */
    private fun configureSslHandler(pipeline: ChannelPipeline) {
        val sslHandler = sslCtx!!.newHandler(pipeline.channel().alloc(), host, port)

        // Enable hostname verification for security
        val sslEngine = sslHandler.engine()
        val sslParams = sslEngine.sslParameters
        sslParams.endpointIdentificationAlgorithm = "HTTPS"
        sslEngine.sslParameters = sslParams

        pipeline.addLast("ssl", sslHandler)
    }

    /**
     * Configures HTTP/1.1 pipeline with codec, decompressor, and response handler.
     */
    private fun configureHttp1Pipeline(pipeline: ChannelPipeline) {
        // HTTP/1.1 codec for encoding requests and decoding responses
        pipeline.addLast("http-codec", HttpClientCodec())

        // HTTP content decompressor for gzip, deflate
        pipeline.addLast("decompressor", HttpContentDecompressor())

        // Custom handler for processing HTTP responses and streaming content
        val handler = NettyHttpHandler(callContext, requestTime)
        NettyHttpHandler.set(pipeline.channel(), handler)
        pipeline.addLast("handler", handler)
    }

    // TODO: Future HTTP/2 Implementation
    // When adding HTTP/2 support, implement:
    // 1. ApplicationProtocolNegotiationHandler to detect negotiated protocol
    // 2. configureHttp2Pipeline() method with proper stream management
    // 3. HttpToHttp2ConnectionHandler or Http2FrameCodec based approach
    // See HTTP2_INVESTIGATION.md for detailed analysis and recommendations
}
