/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Configuration for WebRTC connections.
 *
 * Provides settings for ICE servers, policies, and other parameters needed for WebRTC connections.
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/RTCPeerConnection#configuration">MDN RTCPeerConnection config</a>
 */
@KtorDsl
public open class WebRTCConfig {
    public var mediaTrackFactory: MediaTrackFactory? = null
    public var dispatcher: CoroutineDispatcher? = null

    public var iceServers: List<WebRTC.IceServer> = emptyList()
    public var turnServers: List<WebRTC.IceServer> = emptyList()

    /**
     * Refresh rate of [WebRTC.Stats] in ms.
     * Value less than or equal to zero means there will be no statistics collected.
     * Defaults to -1.
     * */
    public var statsRefreshRate: Long = -1

    public var iceCandidatePoolSize: Int = 0
    public var bundlePolicy: WebRTC.BundlePolicy = WebRTC.BundlePolicy.BALANCED
    public var rtcpMuxPolicy: WebRTC.RTCPMuxPolicy = WebRTC.RTCPMuxPolicy.NEGOTIATE
    public var iceTransportPolicy: WebRTC.IceTransportPolicy = WebRTC.IceTransportPolicy.ALL

    /**
     * Replay for the shared flow of operations on remote tracks (additions, removals). Defaults to 10.
     * */
    public var remoteTracksReplay: Int = 10

    /**
     * Replay for the shared flow of ICE candidates. Defaults to 10.
     * */
    public var iceCandidatesReplay: Int = 20
}

/**
 * Factory interface for creating audio and video media tracks.
 */
public interface MediaTrackFactory {
    public suspend fun createAudioTrack(constraints: WebRTCMedia.AudioTrackConstraints): WebRTCMedia.AudioTrack
    public suspend fun createVideoTrack(constraints: WebRTCMedia.VideoTrackConstraints): WebRTCMedia.VideoTrack
}

/**
 * Core engine interface for WebRTC functionality.
 *
 * Provides the ability to create peer connections and media tracks.
 * Implementations of this interface handle the platform-specific WebRTC operations.
 */
public interface WebRTCEngine : CoroutineScope, Closeable, MediaTrackFactory {
    public val config: WebRTCConfig
    public val dispatcher: CoroutineDispatcher

    /**
     * Creates a new peer connection with the configured settings.
     */
    public suspend fun createPeerConnection(): WebRtcPeerConnection
}

internal expect fun ioDispatcher(): CoroutineDispatcher

/**
 * Base implementation of the WebRTCEngine interface.
 *
 * Provides common functionality for WebRTC engine implementations.
 *
 * @param engineName Name identifier for the engine, used in coroutine naming.
 */
public abstract class WebRTCEngineBase(private val engineName: String) : WebRTCEngine {
    override val dispatcher: CoroutineDispatcher by lazy { config.dispatcher ?: ioDispatcher() }

    override val coroutineContext: CoroutineContext by lazy {
        dispatcher + CoroutineName("$engineName-context")
    }

    override fun close() {}
}
