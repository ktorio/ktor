/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import io.ktor.http.Headers
import io.ktor.http.headers
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.http3.DefaultHttp3DataFrame
import io.netty.handler.codec.http3.DefaultHttp3Headers
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame
import io.netty.handler.codec.http3.Http3
import io.netty.handler.codec.http3.Http3ClientConnectionHandler
import io.netty.handler.codec.http3.Http3DataFrame
import io.netty.handler.codec.http3.Http3ErrorCode
import io.netty.handler.codec.http3.Http3Exception
import io.netty.handler.codec.http3.Http3Headers
import io.netty.handler.codec.http3.Http3HeadersFrame
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler
import io.netty.handler.codec.quic.QuicChannel
import io.netty.handler.codec.quic.QuicException
import io.netty.handler.codec.quic.QuicSslContextBuilder
import io.netty.handler.codec.quic.QuicStreamChannel
import io.netty.handler.codec.quic.QuicStreamResetException
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object Http3TestTimeouts {
    /** Deadline for establishing a QUIC connection, including retries while the server binds UDP. */
    val connect: Duration = 15.seconds

    /** Deadline for a single response to complete, or for a single stream event to arrive. */
    val response: Duration = 10.seconds

    /** Grace period for shutting down the client event loop group. */
    val shutdown: Duration = 1.seconds
}

/**
 * A single event observed on an HTTP/3 request stream, in arrival order.
 *
 * Reading events one at a time is what makes response flush, SSE and time-to-first-byte assertions
 * possible; use [Http3TestStream.awaitResponse] when only the complete response matters.
 */
sealed interface Http3StreamEvent {
    /** The response HEADERS frame: the first HEADERS frame on the stream. */
    data class ResponseHeaders(val status: String?, val headers: Headers) : Http3StreamEvent

    /** One DATA frame, as delivered by the server. */
    class Data(val bytes: ByteArray) : Http3StreamEvent

    /** A trailing HEADERS frame: any HEADERS frame after the response headers. */
    data class Trailers(val headers: Headers) : Http3StreamEvent

    /** The server half-closed the stream: no more response data will arrive. */
    data object InputClosed : Http3StreamEvent

    /**
     * The stream failed. [http3ErrorCode] is the observed HTTP/3 error code, or `null` when the
     * failure came from the QUIC transport rather than the HTTP/3 layer.
     */
    data class Failure(val http3ErrorCode: Int?, val cause: Throwable) : Http3StreamEvent
}

/** A complete HTTP/3 response, accumulated from the events of one request stream. */
class Http3TestResponse(
    val status: String,
    val headers: Headers,
    val trailers: Headers,
    val body: ByteArray,
    val http3ErrorCode: Int? = null,
) {
    val bodyText: String get() = body.toString(Charsets.UTF_8)
}

/**
 * One HTTP/3 request stream. Obtain it from [Http3TestConnection.openStream] to drive the request
 * frame by frame, or use [Http3TestConnection.request] for the common case.
 *
 * Every method blocks: the tests using this client are JVM-only and already run on
 * [Dispatchers.IO] via [withHttp3Client].
 */
class Http3TestStream internal constructor(
    val channel: QuicStreamChannel,
    private val handler: Http3TestStreamHandler,
    private val authority: String,
) {
    /**
     * Sends the request HEADERS frame. Header names are lower-cased, as HTTP/3 requires.
     *
     * Pass `null` for [authority] to omit the `:authority` pseudo-header, which is what drives the
     * server's `serverHost`/`serverPort` fallbacks.
     */
    fun sendHeaders(
        method: String = "GET",
        path: String = "/",
        headers: Map<String, String> = emptyMap(),
        scheme: String = "https",
        authority: String? = this.authority,
        endStream: Boolean = false,
    ) {
        val requestHeaders = DefaultHttp3Headers().apply {
            method(method)
            path(path)
            scheme(scheme)
            authority?.let { authority(it) }
            headers.forEach { (name, value) -> add(name.lowercase(), value) }
        }
        sendRawHeaders(requestHeaders, endStream)
    }

    /**
     * Sends a HEADERS frame exactly as given, bypassing the pseudo-header defaults of [sendHeaders].
     *
     * This is the escape hatch for malformed, duplicated or missing pseudo-headers, which cannot be
     * expressed through a well-behaved request builder.
     */
    fun sendRawHeaders(headers: Http3Headers, endStream: Boolean = false) {
        channel.writeAndFlush(DefaultHttp3HeadersFrame(headers)).sync()
        if (endStream) endOutput()
    }

    /**
     * Sends one DATA frame. Call it repeatedly to produce a multi-frame request body, which is the
     * only way to exercise the server's incremental request reads.
     */
    fun sendData(bytes: ByteArray, endStream: Boolean = false) {
        channel.writeAndFlush(DefaultHttp3DataFrame(Unpooled.copiedBuffer(bytes))).sync()
        if (endStream) endOutput()
    }

    /** Sends a trailing HEADERS frame and, by default, ends the request. */
    fun sendTrailers(trailers: Map<String, String>, endStream: Boolean = true) {
        val trailingHeaders = DefaultHttp3Headers().apply {
            trailers.forEach { (name, value) -> add(name.lowercase(), value) }
        }
        channel.writeAndFlush(DefaultHttp3HeadersFrame(trailingHeaders)).sync()
        if (endStream) endOutput()
    }

    /** Half-closes the request side (QUIC stream FIN), telling the server the request is complete. */
    fun endOutput() {
        channel.shutdownOutput().sync()
    }

    /**
     * Aborts this stream with `RESET_STREAM` + `STOP_SENDING`, leaving the rest of the connection
     * usable. Use it to assert that one aborted request does not disturb its siblings.
     */
    fun reset(errorCode: Int = Http3ErrorCode.H3_REQUEST_CANCELLED.code()) {
        channel.shutdown(errorCode).sync()
    }

    /** The next event on this stream, or `null` if none arrived within [timeout]. */
    fun nextEvent(timeout: Duration = Http3TestTimeouts.response): Http3StreamEvent? =
        handler.events.poll(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

    /**
     * Closes the local end of the stream.
     *
     * Worth doing for every stream a test finishes with: until a stream is closed the local QUIC
     * stack keeps it alive, and concurrent request loops can then starve on stream credit long
     * before the server's own limit is the constraint.
     */
    fun close() {
        runCatching { channel.close().sync() }
    }

    /**
     * Accumulates events until the stream input closes or fails, and returns the whole response.
     *
     * @throws IllegalStateException if the response does not complete within [timeout].
     */
    fun awaitResponse(timeout: Duration = Http3TestTimeouts.response): Http3TestResponse {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        var status = ""
        var headers = Headers.Empty
        var trailers = Headers.Empty
        var errorCode: Int? = null
        val body = ByteArrayOutputStream()

        loop@ while (true) {
            val remaining = deadline - System.nanoTime()
            val event = if (remaining <= 0) null else handler.events.poll(remaining, TimeUnit.NANOSECONDS)
            when (event) {
                null -> error("Timed out after $timeout waiting for the HTTP/3 response to complete")

                is Http3StreamEvent.ResponseHeaders -> {
                    status = event.status.orEmpty()
                    headers = event.headers
                }

                is Http3StreamEvent.Data -> body.write(event.bytes)

                is Http3StreamEvent.Trailers -> trailers = event.headers

                is Http3StreamEvent.Failure -> {
                    errorCode = event.http3ErrorCode
                    break@loop
                }

                Http3StreamEvent.InputClosed -> break@loop
            }
        }

        return Http3TestResponse(status, headers, trailers, body.toByteArray(), errorCode)
    }
}

/**
 * One QUIC connection to a Ktor HTTP/3 server. Streams opened from it are independent, so tests can
 * interleave several requests on a single connection.
 */
class Http3TestConnection internal constructor(
    val quicChannel: QuicChannel,
    val authority: String,
    private val udpChannel: Channel,
    private val group: EventLoopGroup,
) : AutoCloseable {
    /** Closes the QUIC connection, its UDP socket and the client event loop. */
    override fun close() {
        runCatching { quicChannel.close().sync() }
        runCatching { udpChannel.close().sync() }
        runCatching {
            group.shutdownGracefully(0, Http3TestTimeouts.shutdown.inWholeMilliseconds, TimeUnit.MILLISECONDS).sync()
        }
    }

    /** Opens a request stream without sending anything yet. */
    fun openStream(): Http3TestStream {
        val handler = Http3TestStreamHandler()
        val stream = Http3.newRequestStream(quicChannel, handler).sync().getNow()
        return Http3TestStream(stream, handler, authority)
    }

    /**
     * Sends a complete request on a new stream and returns the response.
     *
     * Pass [bodyChunks] instead of [body] to split the request body across several DATA frames.
     */
    fun request(
        method: String = "GET",
        path: String = "/",
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        bodyChunks: List<ByteArray> = listOfNotNull(body),
        trailers: Map<String, String> = emptyMap(),
        timeout: Duration = Http3TestTimeouts.response,
    ): Http3TestResponse {
        val stream = openStream()
        try {
            stream.sendHeaders(method, path, headers)
            bodyChunks.forEach { stream.sendData(it) }
            if (trailers.isEmpty()) stream.endOutput() else stream.sendTrailers(trailers)
            return stream.awaitResponse(timeout)
        } finally {
            stream.close()
        }
    }
}

/**
 * Opens a QUIC connection to the HTTP/3 server on [port]. The caller owns it and must
 * [Http3TestConnection.close] it; use [withHttp3Client] unless the connection has to outlive a
 * single block, as it does inside [Http3TestClientEngine].
 *
 * The handshake is retried until [connectTimeout] elapses, because `createAndStartServer` only
 * waits for the TCP connectors to accept while the UDP listener may still be binding.
 */
suspend fun openHttp3Connection(
    port: Int,
    host: String = "127.0.0.1",
    authority: String = "localhost:$port",
    connectTimeout: Duration = Http3TestTimeouts.connect,
    inboundDatagrams: AtomicLong? = null,
): Http3TestConnection = withContext(Dispatchers.IO) {
    val group = MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
    var udpChannel: Channel? = null

    try {
        udpChannel = Bootstrap()
            .group(group)
            .channel(NioDatagramChannel::class.java)
            .handler(clientPipeline(inboundDatagrams))
            .bind(0)
            .sync()
            .channel()

        val quicChannel = connectQuic(udpChannel, InetSocketAddress(host, port), connectTimeout)
        Http3TestConnection(quicChannel, authority, udpChannel, group)
    } catch (cause: Throwable) {
        runCatching { udpChannel?.close()?.sync() }
        runCatching {
            group.shutdownGracefully(0, Http3TestTimeouts.shutdown.inWholeMilliseconds, TimeUnit.MILLISECONDS).sync()
        }
        throw cause
    }
}

/**
 * Connects to the HTTP/3 server on [port] and runs [block] with the established QUIC connection,
 * closing the connection and the client event loop afterwards.
 */
suspend fun <T> withHttp3Client(
    port: Int,
    host: String = "127.0.0.1",
    authority: String = "localhost:$port",
    connectTimeout: Duration = Http3TestTimeouts.connect,
    inboundDatagrams: AtomicLong? = null,
    block: suspend (Http3TestConnection) -> T,
): T {
    val connection = openHttp3Connection(port, host, authority, connectTimeout, inboundDatagrams)
    return try {
        block(connection)
    } finally {
        connection.close()
    }
}

/**
 * Waits until an HTTP/3 handshake to [port] succeeds.
 *
 * A UDP listener cannot be probed the way `waitForPort` probes TCP: sends to an unbound UDP port
 * are silently discarded, and once the server sets `SO_REUSEPORT` a competing bind succeeds too.
 * Readiness is therefore established by completing an actual QUIC handshake.
 */
suspend fun waitForHttp3Port(
    port: Int,
    host: String = "127.0.0.1",
    timeout: Duration = Http3TestTimeouts.connect,
) {
    withHttp3Client(port, host, connectTimeout = timeout) { }
}

/**
 * The datagram channel's pipeline: the QUIC codec, optionally preceded by a counter.
 *
 * Counting datagrams is how a test can assert on packetisation — for instance that a small
 * response's last DATA frame and its stream FIN leave the server in one datagram — without
 * resorting to timing.
 */
private fun clientPipeline(inboundDatagrams: AtomicLong?): ChannelHandler {
    val codec = http3ClientCodec()
    if (inboundDatagrams == null) return codec

    return object : ChannelInitializer<Channel>() {
        override fun initChannel(channel: Channel) {
            channel.pipeline().addLast(DatagramCountingHandler(inboundDatagrams))
            channel.pipeline().addLast(codec)
        }
    }
}

private class DatagramCountingHandler(private val counter: AtomicLong) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        counter.incrementAndGet()
        super.channelRead(ctx, msg)
    }
}

private fun http3ClientCodec(): ChannelHandler {
    val sslContext = QuicSslContextBuilder.forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .applicationProtocols(*Http3.supportedApplicationProtocols())
        .build()

    return Http3.newQuicClientCodecBuilder()
        .sslContext(sslContext)
        .maxIdleTimeout(30_000, TimeUnit.MILLISECONDS)
        .initialMaxData(10_000_000)
        .initialMaxStreamDataBidirectionalLocal(1_000_000)
        .initialMaxStreamDataBidirectionalRemote(1_000_000)
        .initialMaxStreamsBidirectional(100)
        .build()
}

private fun connectQuic(udpChannel: Channel, address: InetSocketAddress, timeout: Duration): QuicChannel {
    val deadline = System.nanoTime() + timeout.inWholeNanoseconds
    var lastFailure: Throwable? = null

    while (true) {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) break

        try {
            return QuicChannel.newBootstrap(udpChannel)
                .handler(Http3ClientConnectionHandler())
                .remoteAddress(address)
                .connect()
                .get(minOf(remaining, HANDSHAKE_ATTEMPT_TIMEOUT_NANOS), TimeUnit.NANOSECONDS)
        } catch (cause: Exception) {
            // The server may not have bound UDP yet; retry until the deadline.
            lastFailure = cause
            Thread.sleep(HANDSHAKE_RETRY_DELAY_MILLIS)
        }
    }

    throw IllegalStateException("Failed to establish a QUIC connection to $address within $timeout", lastFailure)
}

private const val HANDSHAKE_RETRY_DELAY_MILLIS = 50L
private val HANDSHAKE_ATTEMPT_TIMEOUT_NANOS = 2.seconds.inWholeNanoseconds

internal class Http3TestStreamHandler : Http3RequestStreamInboundHandler() {
    val events: BlockingQueue<Http3StreamEvent> = LinkedBlockingQueue()

    private var responseHeadersSeen = false

    override fun channelRead(ctx: ChannelHandlerContext, frame: Http3HeadersFrame) {
        val headers = headers {
            frame.headers().forEach { (name, value) ->
                val header = name.toString()
                if (!header.startsWith(":")) append(header, value.toString())
            }
        }

        if (responseHeadersSeen) {
            events.put(Http3StreamEvent.Trailers(headers))
        } else {
            responseHeadersSeen = true
            events.put(Http3StreamEvent.ResponseHeaders(frame.headers().status()?.toString(), headers))
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext, frame: Http3DataFrame) {
        val content = frame.content()
        val bytes = ByteArray(content.readableBytes())
        content.readBytes(bytes)
        frame.release()
        events.put(Http3StreamEvent.Data(bytes))
    }

    override fun channelInputClosed(ctx: ChannelHandlerContext) {
        events.put(Http3StreamEvent.InputClosed)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        // A connection-level close does not raise ChannelInputShutdownEvent, so waiters would
        // otherwise block until their timeout.
        events.put(Http3StreamEvent.InputClosed)
        super.channelInactive(ctx)
    }

    override fun handleHttp3Exception(ctx: ChannelHandlerContext, exception: Http3Exception) {
        events.put(Http3StreamEvent.Failure(exception.errorCode().code(), exception))
    }

    override fun handleQuicException(ctx: ChannelHandlerContext, exception: QuicException) {
        // A peer RESET_STREAM arrives here, not through handleHttp3Exception, and carries the
        // application error code — which for an HTTP/3 peer is an Http3ErrorCode. Reading it is the
        // only way a test can see the code the server actually sent.
        val errorCode = (exception as? QuicStreamResetException)
            ?.applicationProtocolCode()
            ?.takeIf { it >= 0 }
            ?.toInt()
        events.put(Http3StreamEvent.Failure(errorCode, exception))
    }
}
