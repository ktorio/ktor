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
     * The [QuicTokenHandler] used to generate and validate QUIC retry tokens.
     * By default, a secure HMAC-SHA256-based handler is used that cryptographically
     * signs tokens with a randomly generated key and rejects forged or expired tokens.
     *
     * Callers may replace this with a custom [QuicTokenHandler] implementation
     * to use a different signing strategy or integrate with external token services.
     */
    public var quicTokenHandler: QuicTokenHandler = HmacQuicTokenHandler()

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
     * User-provided function to configure the QUIC server codec builder.
     * This lambda is invoked on the [QuicServerCodecBuilder] after all default
     * settings have been applied, allowing callers to override or add any
     * QUIC transport parameters.
     */
    public var configureQuicServerCodec: QuicServerCodecBuilder.() -> Unit = {}
}
