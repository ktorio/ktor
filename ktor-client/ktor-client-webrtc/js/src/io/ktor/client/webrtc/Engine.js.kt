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

    override suspend fun createPeerConnection(connectionConfig: WebRtcConnectionConfig?): WebRtcPeerConnection {
        val config = connectionConfig ?: WebRtcConnectionConfig().apply(config.defaultConnectionConfig)
        val rtcConfig = jsObject<RTCConfiguration> {
            bundlePolicy = config.bundlePolicy.toJs()
            rtcpMuxPolicy = config.rtcpMuxPolicy.toJs()
            iceCandidatePoolSize = config.iceCandidatePoolSize
            iceTransportPolicy = config.iceTransportPolicy.toJs()
            iceServers = config.iceServers.map { it.toJs() }.toTypedArray()
        }
        val peerConnection = RTCPeerConnection(rtcConfig)
        return JsWebRtcPeerConnection(
            peerConnection,
            coroutineContext,
            config
        )
    }
}

public actual object JsWebRtc : WebRtcClientEngineFactory<JsWebRtcEngineConfig> {
    actual override fun create(block: JsWebRtcEngineConfig.() -> Unit): WebRtcEngine =
        JsWebRtcEngine(JsWebRtcEngineConfig().apply(block))
}
