/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

public const val WEBRTC_STATISTICS_DISABLED: Long = -1

/**
 * Configuration for WebRtc connections.
 *
 * Provides settings for ICE servers, policies, and other parameters needed for WebRtc connections.
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/RTCPeerConnection#configuration">MDN RTCPeerConnection config</a>
 */
@KtorDsl
public open class WebRtcConnectionConfig {
    public var iceServers: List<WebRtc.IceServer> = emptyList()

    /**
     * Refresh rate of [WebRtc.Stats] in ms.
     * Value less than or equal to zero means there will be no statistics collected.
     * Defaults to NO_WEBRTC_STATISTICS (-1).
     * */
    public var statsRefreshRate: Long = WEBRTC_STATISTICS_DISABLED

    public var iceCandidatePoolSize: Int = 0
    public var bundlePolicy: WebRtc.BundlePolicy = WebRtc.BundlePolicy.BALANCED
    public var rtcpMuxPolicy: WebRtc.RTCPMuxPolicy = WebRtc.RTCPMuxPolicy.REQUIRE
    public var iceTransportPolicy: WebRtc.IceTransportPolicy = WebRtc.IceTransportPolicy.ALL

    /**
     * Replay for the shared flow of operations on remote tracks (additions, removals). Defaults to 10.
     * */
    public var remoteTracksReplay: Int = 10

    /**
     * Replay for the shared flow of ICE candidates. Defaults to 20.
     * */
    public var iceCandidatesReplay: Int = 20
}

/**
 * Configuration for the WebRtc client.
 */
@KtorDsl
public open class WebRtcConfig {
    public var dispatcher: CoroutineDispatcher? = null
    public var mediaTrackFactory: MediaTrackFactory? = null
    public var defaultConnectionConfig: (WebRtcConnectionConfig.() -> Unit) = {}
}

/**
 * Core engine interface for WebRtc functionality.
 *
 * Provides the ability to create peer connections and media tracks.
 * Implementations of this interface handle the platform-specific WebRtc operations.
 */
public interface WebRtcEngine : CoroutineScope, Closeable, MediaTrackFactory {
    public val config: WebRtcConfig

    /**
     * Creates a new peer connection with the configured settings. If no configuration is provided,
     * the default configuration from [WebRtcConfig.defaultConnectionConfig] will be used.
     */
    public suspend fun createPeerConnection(connectionConfig: WebRtcConnectionConfig? = null): WebRtcPeerConnection
}

/**
 * Exception used as a cancellation cause when WebRtcEngine coroutine context is closed.
 */
public class WebRtcEngineClosedException :
    kotlinx.coroutines.CancellationException("WebRtc engine is closed.")

/**
 * Base implementation of the WebRtcEngine interface.
 *
 * Provides common functionality for WebRtc engine implementations.
 *
 * @param engineName Name identifier for the engine, used in coroutine naming.
 */
public abstract class WebRtcEngineBase(private val engineName: String) : WebRtcEngine {

    override val coroutineContext: CoroutineContext by lazy {
        val dispatcher = config.dispatcher ?: ioDispatcher()
        dispatcher + CoroutineName("$engineName-context")
    }

    override fun close() {
        coroutineContext.cancel(WebRtcEngineClosedException())
    }
}
