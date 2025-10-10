/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import web.rtc.RTCPeerConnection

public external interface JsPeerConnectionConfig

public class JsWebRtcEngineConfig : WebRtcConfig()

/**
 * Common WebRtc Engine factory interface for JS and WasmJS targets.
 **/
public object JsWebRtc : WebRtcClientEngineFactory<JsWebRtcEngineConfig> {
    override fun create(block: JsWebRtcEngineConfig.() -> Unit): WebRtcEngine =
        JsWebRtcEngine(JsWebRtcEngineConfig().apply(block))
}

public class JsWebRtcEngine(
    config: JsWebRtcEngineConfig,
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: NavigatorMediaDevices
) : WebRtcEngineBase("js-webrtc", config),
    MediaTrackFactory by mediaTrackFactory {

    override suspend fun createPeerConnection(config: WebRtcConnectionConfig): WebRtcPeerConnection {
        val nativePeerConnection = RTCPeerConnection(configuration = config.toJs())
        val coroutineContext = createConnectionContext(config.exceptionHandler)
        return JsWebRtcPeerConnection(nativePeerConnection, coroutineContext, config)
    }
}
