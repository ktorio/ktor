/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.*

public interface WebRTCClientEngineFactory<out T : WebRTCConfig> {
    public fun create(block: T.() -> Unit = {}): WebRTCEngine
}

/**
 * A multiplatform WebRTC client for establishing peer-to-peer connections,
 * managing media streams, and handling signaling.
 *
 * # Creating a client
 * ```kotlin
 * val client = WebRTCClient()
 * ```
 *
 * # Creating with specific engine
 * ```kotlin
 * val client = WebRTCClient(WebRTCJsEngine())
 * ```
 *
 * # Closing the client
 * ```kotlin
 * client.close()
 * ```
 */
public class WebRTCClient(public val engine: WebRTCEngine) {
    /**
     * The underlying WebRTCClientEngine instance.
     */
    public val engineConfig: WebRTCConfig = engine.config

    /**
     * Creates a new peer connection with optional configuration.
     *
     * @return configured peer connection
     */
    public suspend fun createPeerConnection(): WebRtcPeerConnection {
        return engine.createPeerConnection()
    }

    /**
     * Creates an audio track.
     */
    public suspend fun createAudioTrack(): WebRTCMediaTrack = engine.createAudioTrack()

    /**
     * Creates a video track.
     */
    public suspend fun createVideoTrack(): WebRTCMediaTrack = engine.createVideoTrack()

    /**
     * Closes this client and releases all allocated resources.
     */
    public fun close(): Unit = engine.close()
}

/**
 * Creates a [WebRTCClient] with a specified engine.
 *
 * @param block configuration block for the client
 */
@KtorDsl
public fun <T : WebRTCConfig> WebRTCClient(
    factory: WebRTCClientEngineFactory<T>,
    block: WebRTCConfig.() -> Unit = {}
): WebRTCClient = WebRTCClient(factory.create(block))

/**
 * Creates a [WebRTCClient] with a default engine selected from the available implementations.
 *
 * @param block configuration block for the client
 */
@OptIn(InternalAPI::class)
@KtorDsl
public fun WebRTCClient(block: WebRTCConfig.() -> Unit = {}): WebRTCClient {
    val factory = DefaultWebRTCEngine.factory ?: error("WebRTC client engine is not specified.")
    return WebRTCClient(factory.create(block))
}


@InternalAPI
public object DefaultWebRTCEngine {
    public var factory: WebRTCClientEngineFactory<WebRTCConfig>? = null
}
