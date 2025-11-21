/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.netty

import io.ktor.client.call.UnsupportedContentTypeException
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.netty.bootstrap.*
import io.netty.buffer.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.nio.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import io.netty.handler.ssl.*
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.time.*
import java.time.temporal.*
import kotlin.coroutines.*

public class NettyHttpEngine(override val config: NettyHttpConfig) : HttpClientEngineBase("ktor-netty") {

    private val protocolVersion = config.protocolVersion

    public override val supportedCapabilities: Set<HttpClientEngineCapability<*>> =
        setOf(HttpTimeoutCapability, WebSocketCapability, SSECapability)

    // Consider usnig io.ktor.server.netty.EventLoopGroupProxy
    private val eventLoopGroup: EventLoopGroup = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())

    init {
        coroutineContext.job.invokeOnCompletion {
            eventLoopGroup.shutdownGracefully()
        }
    }

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()

        return try {
            if (data.isUpgradeRequest()) {
                executeWebSocketRequest(callContext, data)
            } else {
                executeHttpRequest(callContext, data)
            }
        } catch (cause: Throwable) {
            callContext.cancel(CancellationException("Failed to execute request", cause))
            throw cause
        }
    }

    private suspend fun executeHttpRequest(
        callContext: CoroutineContext,
        requestData: HttpRequestData
    ): HttpResponseData = withContext(callContext) {
        val requestTime = GMTDate()
        val host = requestData.url.host
        val port = requestData.url.port
        val secure = requestData.url.protocol.isSecure()

        // Determine connection target (proxy or direct)
        val proxy = config.proxy
        val connectHost: String
        val connectPort: Int
        val needsProxyTunnel: Boolean

        when {
            proxy != null && proxy.type() == Proxy.Type.HTTP -> {
                val proxyAddress = proxy.address() as InetSocketAddress
                connectHost = proxyAddress.hostString
                connectPort = proxyAddress.port
                // Need tunnel for HTTPS over HTTP proxy
                needsProxyTunnel = secure
            }
            else -> {
                connectHost = host
                connectPort = port
                needsProxyTunnel = false
            }
        }

        // Determine if HTTP/2 is requested
        // Note: HTTP/2 support is not yet implemented; requests will fall back to HTTP/1.1
        // See HTTP2_INVESTIGATION.md for details
        val useHttp2 = config.protocolVersion == java.net.http.HttpClient.Version.HTTP_2

        // Create SSL context if needed (but don't add to pipeline yet if using proxy tunnel)
        val sslCtx = if (secure) {
            val configuredSslContext = config.sslContext
            if (configuredSslContext != null) {
                // Use custom SSL context if provided
                JdkSslContext(
                    configuredSslContext,
                    true, // isClient
                    null, // ciphers
                    IdentityCipherSuiteFilter.INSTANCE,
                    null, // apn
                    ClientAuth.NONE,
                    null, // protocols
                    false // startTls
                )
            } else {
                // Use default system trust manager (validates certificates and hostnames)
                val builder = SslContextBuilder.forClient()

                // TODO: Future HTTP/2 Support
                // ALPN configuration is kept here for future HTTP/2 implementation.
                // When HTTP/2 is fully implemented, the ApplicationProtocolNegotiationHandler
                // in NettyClientInitializer will use this configuration to negotiate protocol.
                if (useHttp2 && alpnProvider != null) {
                    builder.sslProvider(alpnProvider)
                    builder.ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    builder.applicationProtocolConfig(
                        ApplicationProtocolConfig(
                            ApplicationProtocolConfig.Protocol.ALPN,
                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2,
                            ApplicationProtocolNames.HTTP_1_1
                        )
                    )
                }

                builder.build()
            }
        } else {
            null
        }

        // Get connect timeout from capability
        val connectTimeoutMillis = requestData.getCapabilityOrNull(HttpTimeoutCapability)
            ?.connectTimeoutMillis

        // Setup bootstrap
        val bootstrap = Bootstrap()
        bootstrap.group(eventLoopGroup)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.TCP_NODELAY, true)

        // Set connect timeout if specified
        if (connectTimeoutMillis != null && connectTimeoutMillis > 0 && !isTimeoutInfinite(connectTimeoutMillis)) {
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis.toInt())
        }

        // Use different initializer for proxy tunnel
        if (needsProxyTunnel) {
            bootstrap.handler(ProxyTunnelInitializer(host, port, callContext))
        } else {
            bootstrap.handler(
                NettyClientInitializer(sslCtx, host, port, callContext, requestTime, useHttp2)
            )
        }

        config.bootstrapConfig(bootstrap)

        // Connect to the server (or proxy)
        val channelFuture = bootstrap.connect(connectHost, connectPort)
        val channel = try {
            channelFuture.awaitNetty()
            if (!channelFuture.isSuccess) {
                val cause = channelFuture.cause()
                // Check if it's a connect timeout
                if (cause is io.netty.channel.ConnectTimeoutException) {
                    throw ConnectTimeoutException(requestData, cause)
                }
                throw cause ?: IOException("Failed to connect to $host:$port")
            }
            channelFuture.channel()
        } catch (cause: io.netty.channel.ConnectTimeoutException) {
            throw ConnectTimeoutException(requestData, cause)
        } catch (cause: ConnectTimeoutException) {
            throw cause
        } catch (cause: Throwable) {
            throw IOException("Failed to connect to $connectHost:$connectPort", cause)
        }

        // If using a proxy tunnel, establish it first, then add SSL
        if (needsProxyTunnel && sslCtx != null) {
            val tunnelHandler = ProxyConnectHandler.get(channel)
                ?: throw IllegalStateException("Proxy tunnel handler not found")

            try {
                tunnelHandler.awaitConnect()

                // Tunnel established, now add SSL handler and our handlers
                val pipeline = channel.pipeline()
                pipeline.addFirst("ssl", sslCtx.newHandler(channel.alloc(), host, port))

                // Add HTTP codec and decompressor
                pipeline.addLast("http-codec", HttpClientCodec())
                pipeline.addLast("decompressor", HttpContentDecompressor(config.maxDecompressorAllocation))

                // Add our response handler
                val handler = NettyHttpHandler(callContext, requestTime)
                NettyHttpHandler.set(channel, handler)
                pipeline.addLast("handler", handler)
            } catch (cause: Throwable) {
                channel.close()
                throw IOException("Failed to establish proxy tunnel", cause)
            }
        }

        try {
            // Build the HTTP request
            val nettyRequest = buildNettyRequest(requestData)

            // Write the request headers
            channel.write(nettyRequest)

            // Write the body if present
            when (val body = requestData.body) {
                is OutgoingContent.ByteArrayContent -> {
                    val content = DefaultLastHttpContent(Unpooled.wrappedBuffer(body.bytes()))
                    channel.writeAndFlush(content).awaitNetty()
                }
                is OutgoingContent.ReadChannelContent -> {
                    writeChannelContent(channel, body.readFrom())
                }
                is OutgoingContent.WriteChannelContent -> {
                    val bodyChannel = ByteChannel()
                    launch {
                        body.writeTo(bodyChannel)
                        bodyChannel.close()
                    }
                    writeChannelContent(channel, bodyChannel)
                }
                is OutgoingContent.NoContent -> {
                    // Send empty last content for requests without body
                    channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).awaitNetty()
                }
                is OutgoingContent.ContentWrapper -> {
                    // Handle wrapped content
                    channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).awaitNetty()
                }
                is OutgoingContent.ProtocolUpgrade -> {
                    throw UnsupportedContentTypeException(body)
                }
            }

            // Wait for the response
            val handler = NettyHttpHandler.get(channel)
                ?: throw IllegalStateException("Handler not found in channel")

            handler.awaitResponse()
        } catch (cause: Throwable) {
            channel.close()
            throw cause
        }
    }

    private suspend fun writeChannelContent(channel: io.netty.channel.Channel, content: ByteReadChannel) {
        try {
            val buffer = ByteArray(8192)
            while (!content.isClosedForRead) {
                val bytesRead = content.readAvailable(buffer)
                if (bytesRead > 0) {
                    val byteBuf = Unpooled.wrappedBuffer(buffer, 0, bytesRead)
                    val httpContent = DefaultHttpContent(byteBuf)
                    channel.writeAndFlush(httpContent).awaitNetty()
                }
            }
            // Send last content to signal end of request
            channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).awaitNetty()
        } finally {
            content.cancel()
        }
    }

    @OptIn(InternalAPI::class)
    private fun buildNettyRequest(requestData: HttpRequestData): io.netty.handler.codec.http.HttpRequest {
        val method = io.netty.handler.codec.http.HttpMethod.valueOf(requestData.method.value)
        val uri = requestData.url.encodedPath + if (requestData.url.encodedQuery.isNotEmpty()) {
            "?" + requestData.url.encodedQuery
        } else {
            ""
        }

        // Use DefaultHttpRequest (not Full) so we can send body separately
        val request = io.netty.handler.codec.http.DefaultHttpRequest(
            HttpVersion.HTTP_1_1,
            method,
            uri
        )

        // Add headers
        val headers = request.headers()
        mergeHeaders(requestData.headers, requestData.body) { key, value ->
            if (!DISALLOWED_HEADERS.contains(key)) {
                headers.add(key, value)
            }
        }

        // Add required headers
        headers.set(HttpHeaderNames.HOST.toString(), requestData.url.host)

        // Add content length if available
        when (val body = requestData.body) {
            is OutgoingContent.ByteArrayContent -> {
                headers.set(HttpHeaderNames.CONTENT_LENGTH.toString(), body.contentLength.toString())
            }
            is OutgoingContent.ReadChannelContent,
            is OutgoingContent.WriteChannelContent -> {
                val contentLength = requestData.body.contentLength
                if (contentLength != null && contentLength >= 0) {
                    headers.set(HttpHeaderNames.CONTENT_LENGTH.toString(), contentLength.toString())
                } else {
                    headers.set(HttpHeaderNames.TRANSFER_ENCODING.toString(), HttpHeaderValues.CHUNKED.toString())
                }
            }
            else -> {
                // No content length needed
            }
        }

        return request
    }

    @OptIn(InternalAPI::class)
    private suspend fun executeWebSocketRequest(
        callContext: CoroutineContext,
        requestData: HttpRequestData
    ): HttpResponseData {
        // For now, delegate to Java HttpClient for WebSocket support
        // TODO: Implement native Netty WebSocket support
        val javaClient = java.net.http.HttpClient.newBuilder()
            .version(protocolVersion)
            .executor(dispatcher.asExecutor())
            .build()

        return javaClient.executeWebSocketRequest(callContext, requestData)
    }

    private companion object {
        val alpnProvider by lazy { findAlpnProvider() }

        fun findAlpnProvider(): SslProvider? {
            // Prefer the JDK ALPN implementation.
            //
            // Netty's OpenSSL-based ALPN support (`SslProvider.OPENSSL`) requires the
            // optional `netty-tcnative` native library. In environments where this
            // library is not present, attempting to check
            // `SslProvider.isAlpnSupported(SslProvider.OPENSSL)` can throw
            // `IllegalArgumentException` during `OpenSsl` static initialization and
            // fail the client before we even try to fall back.
            //
            // To make the engine robust (and keep tests green on setups without
            // `netty-tcnative`), we simply rely on the JDK provider when available.
            return try {
                if (SslProvider.isAlpnSupported(SslProvider.JDK)) {
                    SslProvider.JDK
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            }
        }
    }
}

internal fun isTimeoutInfinite(timeoutMs: Long, now: Instant = Instant.now()): Boolean {
    if (timeoutMs == HttpTimeoutConfig.INFINITE_TIMEOUT_MS) return true
    return try {
        // Check that timeout end date as the number of milliseconds can fit Long type
        now.plus(timeoutMs, ChronoUnit.MILLIS).toEpochMilli()
        false
    } catch (_: ArithmeticException) {
        true
    }
}

private suspend fun ChannelFuture.awaitNetty() {
    return suspendCancellableCoroutine { continuation ->
        addListener { future ->
            if (future.isSuccess) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(future.cause() ?: IOException("Channel operation failed"))
            }
        }
    }
}
