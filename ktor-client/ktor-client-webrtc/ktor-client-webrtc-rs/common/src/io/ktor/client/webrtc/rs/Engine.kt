/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.rs

import io.ktor.client.webrtc.*
import io.ktor.client.webrtc.WebRtc.BundlePolicy.*
import uniffi.ktor_client_webrtc.*

/**
 * Factory for creating WebRTC.rs based engines.
 */
public object RustWebRtc : WebRtcClientEngineFactory<WebRtcConfig> {
    override fun create(block: WebRtcConfig.() -> Unit): WebRtcEngine {
        val config = WebRtcConfig().apply(block)
        return RustWebRtcEngine(config)
    }
}

/**
 * Implementation of WebRtcEngine using Rust and webrtc.rs.
 * This is a simplified implementation that will be expanded once the UniFfi bindings are generated.
 */
public class RustWebRtcEngine(
    config: WebRtcConfig,
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: error(
        "There are no default media track factory when using the common engine. Please provide one in the config."
    )
) : WebRtcEngineBase("webrtc-rs", config),
    MediaTrackFactory by mediaTrackFactory {

    override suspend fun createPeerConnection(config: WebRtcConnectionConfig): WebRtcPeerConnection {
        val nativeConfig = ConnectionConfig(
            iceServers = config.iceServers.map {
                IceServer(listOf(it.urls), it.username ?: "", it.credential ?: "")
            },
            addDefaultTransceivers = true,
            iceCandidatePoolSize = config.iceCandidatePoolSize.toUByte(),
            bundlePolicy = when (config.bundlePolicy) {
                BALANCED -> BundlePolicy.BALANCED
                MAX_BUNDLE -> BundlePolicy.MAX_BUNDLE
                MAX_COMPAT -> BundlePolicy.MAX_COMPAT
            },
            rtcpMuxPolicy = when (config.rtcpMuxPolicy) {
                WebRtc.RtcpMuxPolicy.NEGOTIATE -> RtcpMuxPolicy.NEGOTIATE
                WebRtc.RtcpMuxPolicy.REQUIRE -> RtcpMuxPolicy.REQUIRE
            },
            iceTransportPolicy = when (config.iceTransportPolicy) {
                WebRtc.IceTransportPolicy.ALL -> IceTransportPolicy.ALL
                WebRtc.IceTransportPolicy.RELAY -> IceTransportPolicy.RELAY
            }
        )
        val nativeConnection = makePeerConnection(nativeConfig)
        val coroutineContext = createConnectionContext(config.exceptionHandler)
        return RustWebRtcConnection(nativeConnection, coroutineContext, config)
    }
}
