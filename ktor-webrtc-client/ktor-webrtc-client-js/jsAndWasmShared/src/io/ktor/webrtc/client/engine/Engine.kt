/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client.engine

import io.ktor.webrtc.client.WebRTCClientEngineFactory
import io.ktor.webrtc.client.WebRTCConfig
import io.ktor.webrtc.client.WebRTCEngine

public external interface JsPeerConnectionConfig {
    public var video: Boolean?
    public var audio: Boolean?
}

public class JsWebRTCEngineConfig : WebRTCConfig()

public expect object JsWebRTC : WebRTCClientEngineFactory<JsWebRTCEngineConfig> {
    override fun create(block: JsWebRTCEngineConfig.() -> Unit): WebRTCEngine
}
