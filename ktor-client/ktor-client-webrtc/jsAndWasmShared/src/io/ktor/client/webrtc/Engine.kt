/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

public external interface JsPeerConnectionConfig

public class JsWebRtcEngineConfig : WebRtcConfig()

/**
 * Common WebRtc Engine factory interface for JS and WasmJS targets.
 **/
public expect object JsWebRtc : WebRtcClientEngineFactory<JsWebRtcEngineConfig> {
    override fun create(block: JsWebRtcEngineConfig.() -> Unit): WebRtcEngine
}
