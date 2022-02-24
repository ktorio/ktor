/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.websocket

import io.ktor.http.cio.internals.*
import io.ktor.util.*
import java.util.zip.*

private const val SERVER_MAX_WINDOW_BITS: String = "server_max_window_bits"
private const val CLIENT_NO_CONTEXT_TAKEOVER = "client_no_context_takeover"
private const val SERVER_NO_CONTEXT_TAKEOVER = "server_no_context_takeover"
private const val CLIENT_MAX_WINDOW_BITS = "client_max_window_bits"
private const val PERMESSAGE_DEFLATE = "permessage-deflate"

private const val MAX_WINDOW_BITS: Int = 15
private const val MIN_WINDOW_BITS: Int = 8

/**
 * Compress and decompress WebSocket frames to reduce amount of transferred bytes.
 *
 * Usage
 * ```kotlin
 * install(WebSockets) {
 *     extensions {
 *         install(WebSocketDeflateExtension)
 *     }
 * }
 * ```
 *
 * Implements WebSocket deflate extension from [RFC-7692](https://tools.ietf.org/html/rfc7692).
 * This implementation is using window size = 15 due to limitations of [Deflater] implementation.
 */
@ExperimentalWebSocketExtensionApi
public class WebSocketDeflateExtension internal constructor(
    private val config: Config
) : WebSocketExtension<WebSocketDeflateExtension.Config> {
    override val factory: WebSocketExtensionFactory<Config, out WebSocketExtension<Config>> = WebSocketDeflateExtension

    override val protocols: List<WebSocketExtensionHeader> = config.build()

    private val inflater = Inflater(true)
    private val deflater = Deflater(config.compressionLevel, true)

    private var outgoingContextTakeover: Boolean = true
    private var incomingContextTakeover: Boolean = true

    override fun clientNegotiation(negotiatedProtocols: List<WebSocketExtensionHeader>): Boolean {
        val protocol = negotiatedProtocols.find { it.name == PERMESSAGE_DEFLATE } ?: return false

        incomingContextTakeover = config.serverNoContextTakeOver
        outgoingContextTakeover = config.clientNoContextTakeOver

        for ((key, value) in protocol.parseParameters()) {
            when (key) {
                SERVER_MAX_WINDOW_BITS -> {
                    // This value is a hint for a client and can be ignored.
                }
                CLIENT_MAX_WINDOW_BITS -> {
                    if (value.isBlank()) continue
                    check(value.toInt() == MAX_WINDOW_BITS) { "Only $MAX_WINDOW_BITS window size is supported." }
                }
                SERVER_NO_CONTEXT_TAKEOVER -> {
                    check(value.isBlank()) {
                        "WebSocket $PERMESSAGE_DEFLATE extension parameter $SERVER_NO_CONTEXT_TAKEOVER shouldn't " +
                            "have a value. Current: $value"
                    }
                }
                CLIENT_NO_CONTEXT_TAKEOVER -> {
                    check(value.isBlank()) {
                        "WebSocket $PERMESSAGE_DEFLATE extension parameter $CLIENT_NO_CONTEXT_TAKEOVER shouldn't " +
                            "have a value. Current: $value"
                    }
                }
            }
        }

        return true
    }

    override fun serverNegotiation(requestedProtocols: List<WebSocketExtensionHeader>): List<WebSocketExtensionHeader> {
        val protocol = requestedProtocols.find { it.name == PERMESSAGE_DEFLATE } ?: return emptyList()
        val parameters = mutableListOf<String>()

        for ((key, value) in protocol.parseParameters()) {
            when (key.toLowerCase()) {
                SERVER_MAX_WINDOW_BITS -> {
                    check(value.toInt() == MAX_WINDOW_BITS) { "Only $MAX_WINDOW_BITS window size is supported" }
                }
                CLIENT_MAX_WINDOW_BITS -> {
                    // This value is a hint for a server and can be ignored.
                }
                SERVER_NO_CONTEXT_TAKEOVER -> {
                    check(value.isBlank())

                    outgoingContextTakeover = false
                    parameters.add(SERVER_NO_CONTEXT_TAKEOVER)
                }
                CLIENT_NO_CONTEXT_TAKEOVER -> {
                    check(value.isBlank())

                    incomingContextTakeover = false
                    parameters.add(CLIENT_NO_CONTEXT_TAKEOVER)
                }
                else -> error("Unsupported extension parameter: ($key, $value)")
            }
        }

        return listOf(WebSocketExtensionHeader(PERMESSAGE_DEFLATE, parameters))
    }

    override fun processOutgoingFrame(frame: Frame): Frame {
        if (frame !is Frame.Text && frame !is Frame.Binary) return frame
        if (!config.compressCondition(frame)) return frame

        val deflated = deflater.deflateFully(frame.data)

        if (!outgoingContextTakeover) {
            deflater.reset()
        }

        return Frame.byType(frame.fin, frame.frameType, deflated, rsv1, frame.rsv2, frame.rsv3)
    }

    override fun processIncomingFrame(frame: Frame): Frame {
        if (!frame.rsv1 || (frame !is Frame.Text && frame !is Frame.Binary)) return frame

        val inflated = inflater.inflateFully(frame.data)

        if (!incomingContextTakeover) {
            inflater.reset()
        }

        return Frame.byType(frame.fin, frame.frameType, inflated, !rsv1, frame.rsv2, frame.rsv3)
    }

    /**
     * WebSocket deflate extension configuration.
     */
    public class Config {
        /**
         * Specify if the client drops the deflater state (reset the window) after each frame.
         */
        public var clientNoContextTakeOver: Boolean = false

        /**
         * Specify if the server drops the deflater state (reset the window) after each frame.
         */
        public var serverNoContextTakeOver: Boolean = false

        /**
         * Compression level that is used for outgoing frames in the [Deflate] instance.
         */
        public var compressionLevel: Int = Deflater.DEFAULT_COMPRESSION

        internal var manualConfig: (MutableList<WebSocketExtensionHeader>) -> Unit = {}

        internal var compressCondition: (Frame) -> Boolean = { true }

        /**
         * Configure which protocols should send the client.
         */
        public fun configureProtocols(block: (protocols: MutableList<WebSocketExtensionHeader>) -> Unit) {
            manualConfig = {
                manualConfig(it)
                block(it)
            }
        }

        /**
         * Indicates if the outgoing frame should be compressed.
         *
         * Compress the frame only if all conditions passed.
         */
        public fun compressIf(block: (frame: Frame) -> Boolean) {
            val old = compressCondition
            compressCondition = { block(it) && old(it) }
        }

        /**
         * Specify the minimum size of frame for compression.
         */
        public fun compressIfBiggerThan(bytes: Int) {
            compressIf { frame -> frame.data.size > bytes }
        }

        internal fun build(): List<WebSocketExtensionHeader> {
            val result = mutableListOf<WebSocketExtensionHeader>()

            val parameters = mutableListOf<String>()

            if (clientNoContextTakeOver) {
                parameters += CLIENT_NO_CONTEXT_TAKEOVER
            }

            if (serverNoContextTakeOver) {
                parameters += SERVER_NO_CONTEXT_TAKEOVER
            }

            result += WebSocketExtensionHeader(PERMESSAGE_DEFLATE, parameters)
            manualConfig(result)
            return result
        }
    }

    public companion object : WebSocketExtensionFactory<Config, WebSocketDeflateExtension> {
        override val key: AttributeKey<WebSocketDeflateExtension> = AttributeKey("WebsocketDeflateExtension")
        override val rsv1: Boolean = true
        override val rsv2: Boolean = false
        override val rsv3: Boolean = false

        override fun install(config: Config.() -> Unit): WebSocketDeflateExtension =
            WebSocketDeflateExtension(Config().apply(config))
    }
}
