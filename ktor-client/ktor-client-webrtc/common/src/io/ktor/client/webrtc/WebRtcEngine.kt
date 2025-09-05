/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.CoroutineContext

/**
 * Constant that indicates to WebRTCConnection that there should be no automatic statistics collection.
 * E.g., [WebRtcPeerConnection.stats] flow will not emit any events.
 */
public const val WEBRTC_STATISTICS_DISABLED: Long = -1

/**
 * Configuration for WebRtc connections.
 *
 * Provides settings for ICE servers, policies, and other parameters needed for WebRtc connections.
 * @see [MDN RTCPeerConnection config](https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/RTCPeerConnection#configuration)
 */
@KtorDsl
public open class WebRtcConnectionConfig {
    public var iceServers: List<WebRtc.IceServer> = emptyList()

    /**
     * Refresh rate of [WebRtc.Stats] in ms.
     * Value less than or equal to zero means there will be no statistics collected.
     * You can provide a custom [exceptionHandler] to handle exceptions during a statistics collection.
     * Defaults to [WEBRTC_STATISTICS_DISABLED].
     */
    public var statsRefreshRate: Long = WEBRTC_STATISTICS_DISABLED

    /**
     * The size of the prefetched ICE candidate pool.
     * In some cases, this could speed up the connection establishment process.
     * Defaults to 0 (meaning no candidate prefetching will occur).
     */
    public var iceCandidatePoolSize: Int = 0

    /**
     * Specifies how to handle negotiation of candidates when the remote peer is not compatible with the SDP BUNDLE standard.
     * Defaults to [WebRtc.BundlePolicy.BALANCED].
     */
    public var bundlePolicy: WebRtc.BundlePolicy = WebRtc.BundlePolicy.BALANCED

    /**
     * A string which specifies the RTCP mux policy to use when gathering ICE candidates to support non-multiplexed RTCP.
     * Defaults to [WebRtc.RtcpMuxPolicy.REQUIRE].
     */
    public var rtcpMuxPolicy: WebRtc.RtcpMuxPolicy = WebRtc.RtcpMuxPolicy.REQUIRE

    /**
     * Specifies the ICE transport policy to use when gathering ICE candidates.
     * Defaults to [WebRtc.IceTransportPolicy.ALL].
     */
    public var iceTransportPolicy: WebRtc.IceTransportPolicy = WebRtc.IceTransportPolicy.ALL

    /**
     * Replay for the shared flow of operations on remote tracks (additions, removals). Defaults to 10.
     */
    public var remoteTracksReplay: Int = 10

    /**
     * Replay for the shared flow of data channels events in the connection. Defaults to 10.
     */
    public var dataChannelEventsReplay: Int = 10

    /**
     * Replay for the shared flow of ICE candidates. Defaults to 20.
     */
    public var iceCandidatesReplay: Int = 20

    /**
     * Custom coroutine exception handler that will be used to catch background exceptions (e.g., during a statistics
     * collection, emitting events). If no handler is provided [DefaultExceptionHandler] is used.
     */
    public var exceptionHandler: CoroutineExceptionHandler? = null
}

/**
 * Configuration for the WebRtc client.
 *
 * Provides settings for the WebRtc engine, including the dispatcher to use for coroutines,
 * the media track factory, and default connection configuration.
 */
@KtorDsl
public open class WebRtcConfig {
    /**
     * Dispatcher that will be used for coroutines in the background (e.g., emit events).
     * Defaults to [Dispatchers.IO] if available, or [Dispatchers.Default].
     */
    public var dispatcher: CoroutineDispatcher? = null

    /**
     * Media track factory that will be used to create media tracks.
     * The specific engine chooses the default implementation.
     */
    public var mediaTrackFactory: MediaTrackFactory? = null

    /**
     * Default configuration for the [WebRtcPeerConnection] which is used
     * if no extra config is specified when creating the connection.
     */
    public var defaultConnectionConfig: (WebRtcConnectionConfig.() -> Unit) = {}
}

/**
 * Core engine interface for WebRtc functionality.
 *
 * Provides the ability to create peer connections and media tracks.
 * Implementations of this interface handle the platform-specific WebRtc operations.
 */
public interface WebRtcEngine : AutoCloseable, MediaTrackFactory {
    public val config: WebRtcConfig

    /**
     * Creates a new peer connection with the configured settings.
     */
    public suspend fun createPeerConnection(config: WebRtcConnectionConfig): WebRtcPeerConnection

    /**
     * Creates a new peer connection with the configured settings. If no configuration is provided,
     * the default configuration from [WebRtcConfig.defaultConnectionConfig] will be used.
     */
    public suspend fun createPeerConnection(
        config: (WebRtcConnectionConfig.() -> Unit) = this.config.defaultConnectionConfig
    ): WebRtcPeerConnection {
        return createPeerConnection(WebRtcConnectionConfig().apply(config))
    }
}

/**
 * Exception used as a cancellation cause when WebRtcEngine coroutine context is closed.
 */
public class WebRtcEngineClosedException : CancellationException("WebRtc engine is closed.")

/**
 * Base implementation of the WebRtcEngine interface.
 *
 * Provides common functionality for WebRtc engine implementations.
 *
 * @param engineName Name identifier for the engine, used in coroutine naming.
 */
public abstract class WebRtcEngineBase(
    private val engineName: String,
    configuration: WebRtcConfig
) : WebRtcEngine {
    private val parentJob = SupervisorJob()
    override val config: WebRtcConfig = configuration
    private val dispatcher = configuration.dispatcher ?: ioDispatcher()
    private val defaultExceptionHandler = DefaultExceptionHandler("io.ktor.client.webrtc")
    private val engineContext = parentJob + CoroutineName("$engineName-context") + dispatcher

    /**
     * Creates a new coroutine context for the new connection based on engine context and provided exception handler.
     * Every connection scope is independent of the other connection scopes.
     * All child coroutines of the connection scope are also independent of each other.
     */
    protected fun createConnectionContext(exceptionHandler: CoroutineExceptionHandler?): CoroutineContext {
        return engineContext + SupervisorJob(parentJob) + (exceptionHandler ?: defaultExceptionHandler)
    }

    override fun close() {
        parentJob.cancel(WebRtcEngineClosedException())
    }
}
