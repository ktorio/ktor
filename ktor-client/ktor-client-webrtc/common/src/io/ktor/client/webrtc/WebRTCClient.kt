/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.utils.io.*

/**
 * A multiplatform asynchronous WebRTC client for establishing peer-to-peer connections, managing media tracks.
 */
public class WebRTCClient(public val engine: WebRTCEngine) : WebRTCEngine by engine

public interface WebRTCClientEngineFactory<out T : WebRTCConfig> {
    public fun create(block: T.() -> Unit = {}): WebRTCEngine
}

/**
 * Creates a [WebRTCClient] with a specified engine.
 * ```kotlin
 * val rtcClient = WebRTCClient(AndroidWebRTC) {
 *     iceServers = listOf(WebRTC.IceServer(urls = "stun:stun.l.google.com:19302"))
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
public fun <T : WebRTCConfig> WebRTCClient(
    factory: WebRTCClientEngineFactory<T>,
    block: T.() -> Unit = {}
): WebRTCClient = WebRTCClient(factory.create(block))
