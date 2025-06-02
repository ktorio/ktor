/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

public external interface JsPeerConnectionConfig

public class JsWebRTCEngineConfig : WebRTCConfig()

/**
 * Common WebRTC Engine factory interface for JS and WasmJS targets.
 **/
public expect object JsWebRTC : WebRTCClientEngineFactory<JsWebRTCEngineConfig> {
    override fun create(block: JsWebRTCEngineConfig.() -> Unit): WebRTCEngine
}
