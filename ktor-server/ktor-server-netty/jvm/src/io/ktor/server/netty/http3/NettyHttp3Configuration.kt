/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty.http3

import io.ktor.utils.io.*
import io.netty.handler.codec.quic.QuicServerCodecBuilder
import io.netty.handler.codec.quic.QuicTokenHandler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for the HTTP/3 (QUIC) transport in the Netty engine.
 *
 * Options defined here apply only to the HTTP/3 endpoint. They have no effect
 * on HTTP/1.1 or HTTP/2 connectors.
 *
 * An instance of this class is provided to the lambda passed to
 * [io.ktor.server.netty.NettyApplicationEngine.Configuration.enableHttp3].
 */
@KtorDsl
public class NettyHttp3Configuration {

    /**
     * The [QuicTokenHandler] used to generate and validate QUIC address-validation (Retry) tokens.
     *
     * When `null` (the default), no address validation is performed and connections are accepted
     * on the first Initial packet. Setting a handler enables stateless Retry: every new connection
     * without a token receives a Retry packet and must restart the handshake, which adds one full
     * round trip and roughly doubles the Initial packet processing per connection. Enable it when
     * the endpoint is exposed to untrusted networks and needs protection against source-address
     * spoofing and amplification attacks.
     *
     * [HmacQuicTokenHandler] is provided as a secure implementation that signs tokens
     * with HMAC-SHA256 and rejects forged or expired tokens. Callers may also supply a custom
     * [QuicTokenHandler] to use a different signing strategy or integrate with external token services.
     */
    public var quicTokenHandler: QuicTokenHandler? = null

    /**
     * Maximum idle timeout for QUIC connections.
     * If no data is exchanged within this period, the connection is closed.
     *
     * Must be strictly positive.
     */
    public var quicMaxIdleTimeout: Duration = 30.seconds
        set(value) {
            require(value > Duration.ZERO) {
                "quicMaxIdleTimeout must be > 0, but was $value"
            }
            field = value
        }

    /**
     * The initial value for the maximum amount of data that can be sent
     * on the entire QUIC connection, in bytes.
     *
     * Must be strictly positive.
     */
    public var quicInitialMaxData: Long = 10_000_000
        set(value) {
            require(value > 0) {
                "quicInitialMaxData must be > 0, but was $value"
            }
            field = value
        }

    /**
     * The initial flow-control limit for locally-initiated bidirectional
     * QUIC streams, in bytes.
     *
     * Must be strictly positive.
     */
    public var quicInitialMaxStreamDataBidirectionalLocal: Long = 1_000_000
        set(value) {
            require(value > 0) {
                "quicInitialMaxStreamDataBidirectionalLocal must be > 0, but was $value"
            }
            field = value
        }

    /**
     * The initial flow-control limit for remotely-initiated bidirectional
     * QUIC streams, in bytes.
     *
     * Must be strictly positive.
     */
    public var quicInitialMaxStreamDataBidirectionalRemote: Long = 1_000_000
        set(value) {
            require(value > 0) {
                "quicInitialMaxStreamDataBidirectionalRemote must be > 0, but was $value"
            }
            field = value
        }

    /**
     * The initial maximum number of bidirectional streams that the remote
     * peer is allowed to open.
     *
     * Must be strictly positive.
     */
    public var quicInitialMaxStreamsBidirectional: Long = 100
        set(value) {
            require(value > 0) {
                "quicInitialMaxStreamsBidirectional must be > 0, but was $value"
            }
            field = value
        }

    /**
     * The number of UDP sockets to bind for the HTTP/3 endpoint.
     *
     * All QUIC channels of a socket are served by the single event loop the socket is registered on,
     * so binding one socket pins the entire HTTP/3 endpoint to one thread. Binding multiple sockets
     * with `SO_REUSEPORT` lets the kernel distribute connections across sockets (and therefore across
     * event loops) by hashing the client address.
     *
     * When `null` (the default), the number of worker event loops is used on transports where
     * `SO_REUSEPORT` balances UDP traffic across sockets (Linux/epoll), and a single socket elsewhere.
     * Values greater than 1 require a native transport (epoll or kqueue).
     *
     * Note: kernel hashing is based on the client address, so QUIC connection migration across
     * client addresses is not supported when more than one socket is bound.
     */
    public var udpSocketCount: Int? = null
        set(value) {
            require(value == null || value > 0) {
                "udpSocketCount must be > 0, but was $value"
            }
            field = value
        }

    /**
     * The `SO_RCVBUF` size in bytes for the HTTP/3 UDP sockets, or `0` to use the system default.
     *
     * Under load, an undersized receive buffer causes dropped datagrams, which QUIC treats as packet
     * loss and recovers from at a significant throughput cost. Consider raising this (for example,
     * to a few megabytes) for high-throughput deployments; the effective value may be capped by the
     * operating system.
     */
    public var udpReceiveBufferSize: Int = 0
        set(value) {
            require(value >= 0) {
                "udpReceiveBufferSize must be >= 0, but was $value"
            }
            field = value
        }

    /**
     * The `SO_SNDBUF` size in bytes for the HTTP/3 UDP sockets, or `0` to use the system default.
     */
    public var udpSendBufferSize: Int = 0
        set(value) {
            require(value >= 0) {
                "udpSendBufferSize must be >= 0, but was $value"
            }
            field = value
        }

    /**
     * User-provided function to configure the QUIC server codec builder.
     * This lambda is invoked on the [QuicServerCodecBuilder] after all default
     * settings have been applied, allowing callers to override or add any
     * QUIC transport parameters.
     */
    public var configureQuicServerCodec: QuicServerCodecBuilder.() -> Unit = {}
}
