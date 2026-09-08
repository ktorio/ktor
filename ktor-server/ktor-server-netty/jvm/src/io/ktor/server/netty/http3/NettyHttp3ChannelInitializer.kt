/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http3

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.epoll.Epoll
import io.netty.channel.socket.DatagramChannel
import io.netty.handler.codec.http3.Http3
import io.netty.handler.codec.http3.Http3ServerConnectionHandler
import io.netty.handler.codec.quic.EpollQuicUtils
import io.netty.handler.codec.quic.QuicChannel
import io.netty.handler.codec.quic.QuicChannelOption
import io.netty.handler.codec.quic.QuicCodecDispatcher
import io.netty.handler.codec.quic.QuicConnectionIdGenerator
import io.netty.handler.codec.quic.QuicSslContext
import io.netty.util.concurrent.EventExecutorGroup
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * A [ChannelInitializer] for QUIC/HTTP3 that configures the [DatagramChannel] pipeline
 * with the QUIC server codec and HTTP/3 connection handler.
 *
 * When [useCodecDispatcher] is `true` (more than one `SO_REUSEPORT` socket is bound), a single
 * shared [QuicCodecDispatcher] is installed on every socket instead of a bare codec. The dispatcher
 * encodes the socket index into server-issued connection IDs and re-dispatches datagrams that the
 * kernel's 4-tuple hash delivered to the wrong socket (for example, after connection migration or
 * NAT rebinding), so connection-ID routing stays correct across sockets.
 *
 * [Http3ServerConnectionHandler] is created per incoming [QuicChannel], since HTTP/3
 * connection handlers are not sharable.
 */
internal class NettyHttp3ChannelInitializer(
    private val applicationProvider: () -> Application,
    private val enginePipeline: EnginePipeline,
    private val userContext: CoroutineContext,
    private val callEventGroup: EventExecutorGroup,
    private val runningLimit: Int,
    private val quicSslContext: QuicSslContext,
    private val http3Configuration: NettyHttp3Configuration,
    useCodecDispatcher: Boolean,
) : ChannelInitializer<DatagramChannel>() {

    /**
     * Shared across all bound sockets: [QuicCodecDispatcher.handlerAdded] assigns each socket an
     * index and calls back into [newQuicServerCodec] with an index-aware connection-id generator.
     */
    private val codecDispatcher: QuicCodecDispatcher? = if (useCodecDispatcher) {
        object : QuicCodecDispatcher() {
            override fun initChannel(
                channel: Channel,
                localConnectionIdLength: Int,
                idGenerator: QuicConnectionIdGenerator
            ) {
                channel.pipeline().addLast(newQuicServerCodec(localConnectionIdLength, idGenerator))
            }
        }
    } else {
        null
    }

    override fun initChannel(ch: DatagramChannel) {
        val dispatcher = codecDispatcher
        if (dispatcher != null) {
            ch.pipeline().addLast(dispatcher)
        } else {
            ch.pipeline().addLast(newQuicServerCodec(localConnectionIdLength = null, idGenerator = null))
        }
    }

    private fun newQuicServerCodec(
        localConnectionIdLength: Int?,
        idGenerator: QuicConnectionIdGenerator?
    ): ChannelHandler {
        val application = applicationProvider()

        val streamInitializer = NettyHttp3RequestStreamInitializer(
            enginePipeline,
            application,
            userContext,
            callEventGroup,
            runningLimit
        )

        val builder = Http3.newQuicServerCodecBuilder()
            .sslContext(quicSslContext)
            .maxIdleTimeout(http3Configuration.quicMaxIdleTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .initialMaxData(http3Configuration.quicInitialMaxData)
            .initialMaxStreamDataBidirectionalLocal(http3Configuration.quicInitialMaxStreamDataBidirectionalLocal)
            .initialMaxStreamDataBidirectionalRemote(http3Configuration.quicInitialMaxStreamDataBidirectionalRemote)
            .initialMaxStreamsBidirectional(http3Configuration.quicInitialMaxStreamsBidirectional)
        // When no token handler is configured, Netty accepts connections on the first Initial
        // packet; a configured handler enables stateless Retry (address validation).
        http3Configuration.quicTokenHandler?.let(builder::tokenHandler)

        // GSO: send up to 10 UDP packets per syscall where the kernel supports it.
        // newSegmentedAllocator falls back to SegmentedDatagramPacketAllocator.NONE otherwise.
        if (Epoll.isAvailable()) {
            builder.option(
                QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR,
                EpollQuicUtils.newSegmentedAllocator(10)
            )
        }
        // Custom configuration for codecs lambda
        builder.apply(http3Configuration.configureQuicServerCodec)

        // Applied after user configuration on purpose: with multiple sockets, the dispatcher
        // owns connection-id generation, and overriding it would break cross-socket routing.
        localConnectionIdLength?.let(builder::localConnectionIdLength)
        idGenerator?.let(builder::connectionIdAddressGenerator)

        // Http3ServerConnectionHandler is not sharable: one instance per connection
        builder.handler(object : ChannelInitializer<QuicChannel>() {
            override fun initChannel(ch: QuicChannel) {
                ch.pipeline().addLast(Http3ServerConnectionHandler(streamInitializer))
            }
        })

        return builder.build()
    }
}
