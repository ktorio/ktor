/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.utils.io.*

/**
 * A multiplatform asynchronous WebRtc client for establishing peer-to-peer connections, managing media tracks.
 *
 * This client provides a high-level API for WebRTC communication, delegating actual implementation
 * to platform-specific engines. It supports creating peer connections, managing media tracks,
 * and handling WebRTC signaling.
 */
public class WebRtcClient(engine: WebRtcEngine) : WebRtcEngine by engine

/**
 * Client engine factory interface, used in engine implementations.
 *
 * Platform-specific WebRTC implementations provide their own factory implementing this interface,
 * allowing the creation of appropriate WebRtcEngine instances with platform-specific configurations.
 *
 * @param T expected engine-specific configuration class that extends WebRtcConfig
 */
public fun interface WebRtcClientEngineFactory<out T : WebRtcConfig> {
    public fun create(block: T.() -> Unit): WebRtcEngine
}

/**
 * Creates a [WebRtcClient] with a specified engine.
 * ```kotlin
 * val client = WebRtcClient(JsWebRtc) {
 *     defaultConnectionConfig = {
 *         iceServers = listOf(WebRtc.IceServer("stun:stun.l.google.com:19302"))
 *         statsRefreshRate = 100.milliseconds
 *     }
 * }
 * ```
 *
 * # Closing the client
 * ```kotlin
 * client.close()
 * ```
 * @param block configuration block for the client
 */
@KtorDsl
public fun <T : WebRtcConfig> WebRtcClient(
    factory: WebRtcClientEngineFactory<T>,
    block: T.() -> Unit = {}
): WebRtcClient = WebRtcClient(factory.create(block))
