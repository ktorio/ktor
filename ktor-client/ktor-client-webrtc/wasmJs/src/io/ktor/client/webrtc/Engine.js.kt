/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.browser.*
import io.ktor.client.webrtc.utils.*

public class JsWebRtcEngine(
    override val config: JsWebRtcEngineConfig,
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: NavigatorMediaDevices
) : WebRtcEngineBase("js-webrtc"), MediaTrackFactory by mediaTrackFactory {

    override suspend fun createPeerConnection(config: WebRtcConnectionConfig): WebRtcPeerConnection {
        val rtcConfig = jsObject<RTCConfiguration> {
            bundlePolicy = config.bundlePolicy.toJs().toJsString()
            rtcpMuxPolicy = config.rtcpMuxPolicy.toJs().toJsString()
            iceCandidatePoolSize = config.iceCandidatePoolSize.toJsNumber()
            iceTransportPolicy = config.iceTransportPolicy.toJs().toJsString()
            iceServers = config.iceServers.map { it.toJs() }.toJsArray()
        }
        val peerConnection = RTCPeerConnection(rtcConfig)
        return WasmJsWebRtcPeerConnection(
            peerConnection,
            createConnectionContext(config.coroutinesContext),
            config
        )
    }
}

public actual object JsWebRtc : WebRtcClientEngineFactory<JsWebRtcEngineConfig> {
    actual override fun create(block: JsWebRtcEngineConfig.() -> Unit): WebRtcEngine =
        JsWebRtcEngine(JsWebRtcEngineConfig().apply(block))
}
