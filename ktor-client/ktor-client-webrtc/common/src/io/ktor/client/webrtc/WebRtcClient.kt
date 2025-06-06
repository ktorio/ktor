/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.utils.io.*

/**
 * A multiplatform asynchronous WebRtc client for establishing peer-to-peer connections, managing media tracks.
 */
public class WebRtcClient(engine: WebRtcEngine) : WebRtcEngine by engine

public interface WebRtcClientEngineFactory<out T : WebRtcConfig> {
    public fun create(block: T.() -> Unit = {}): WebRtcEngine
}

/**
 * Creates a [WebRtcClient] with a specified engine.
 * ```kotlin
 * val rtcClient = WebRtcClient(AndroidWebRtc) {
 *     iceServers = listOf(WebRtc.IceServer(urls = "stun:stun.l.google.com:19302"))
 *     statsRefreshRate = 10_000
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
