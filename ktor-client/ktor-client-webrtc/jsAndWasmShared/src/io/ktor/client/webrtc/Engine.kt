/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.media.*
import web.rtc.RTCConfiguration
import web.rtc.RTCPeerConnection
import kotlin.js.undefined

public class JsWebRtcEngineConfig : WebRtcConfig()

/**
 * WebRtc Engine factory interface for JS and WasmJS targets.
 **/
public object JsWebRtc : WebRtcClientEngineFactory<JsWebRtcEngineConfig> {
    override fun create(block: JsWebRtcEngineConfig.() -> Unit): WebRtcEngine =
        JsWebRtcEngine(JsWebRtcEngineConfig().apply(block))
}

public class JsWebRtcEngine(
    override val config: JsWebRtcEngineConfig,
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: NavigatorMediaDevices
) : WebRtcEngineBase("js-webrtc"), MediaTrackFactory by mediaTrackFactory {

    override suspend fun createPeerConnection(connectionConfig: WebRtcConnectionConfig?): WebRtcPeerConnection {
        val config = connectionConfig ?: WebRtcConnectionConfig().apply(config.defaultConnectionConfig)

        @Suppress("NON_EXTERNAL_TYPE_EXTENDS_EXTERNAL_TYPE")
        val rtcConfig = object : RTCConfiguration {
            override val certificates = undefined
            override val bundlePolicy = config.bundlePolicy.toJs()
            override val rtcpMuxPolicy = config.rtcpMuxPolicy.toJs()
            override val iceServers = config.iceServers.map { it.toJs() }.toJs()
            override val iceTransportPolicy = config.iceTransportPolicy.toJs()
            override val iceCandidatePoolSize = config.iceCandidatePoolSize.toShort()
        }
        val peerConnection = RTCPeerConnection(rtcConfig)
        return JsWebRtcPeerConnection(peerConnection, coroutineContext, config)
    }
}
