/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import WebRTC.RTCConfiguration
import WebRTC.RTCIceServer
import WebRTC.RTCMediaConstraints
import WebRTC.RTCPeerConnectionFactory
import WebRTC.RTCSdpSemantics
import io.ktor.client.webrtc.media.IosMediaDevices
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Configuration for the iOS WebRTC engine that extends the base WebRTC configuration.
 *
 * @property rtcFactory Optional custom [RTCPeerConnectionFactory] for creating peer connections.
 * If not provided, the factory from the [MediaTrackFactory] will be used.
 */
public class IosWebRtcEngineConfig : WebRtcConfig() {
    @OptIn(ExperimentalForeignApi::class)
    public var rtcFactory: RTCPeerConnectionFactory? = null
}

/**
 * iOS-specific WebRTC engine implementation that handles peer connection creation and management.
 *
 * This engine provides iOS-specific WebRTC functionality by wrapping the native iOS WebRTC framework
 * and implementing the common WebRTC engine interface.
 *
 * @param config The iOS-specific WebRTC engine configuration
 * @param mediaTrackFactory Factory for creating media tracks, defaults to IosMediaDevices if not specified in config
 */
@OptIn(ExperimentalForeignApi::class)
public class IosWebRtcEngine(
    override val config: IosWebRtcEngineConfig,
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: IosMediaDevices()
) : WebRtcEngineBase("ios-webrtc", config), MediaTrackFactory by mediaTrackFactory {

    private val localFactory: RTCPeerConnectionFactory
        get() = config.rtcFactory
            ?: (mediaTrackFactory as? IosMediaDevices)?.peerConnectionFactory
            ?: error("Please specify custom rtcFactory for custom MediaTrackFactory")

    private fun WebRtc.IceServer.toIos(): RTCIceServer {
        return RTCIceServer(urls, username, credential)
    }

    override suspend fun createPeerConnection(config: WebRtcConnectionConfig): WebRtcPeerConnection {
        val iceServers = config.iceServers.map { it.toIos() }
        val rtcConfig = RTCConfiguration().also {
            it.iceServers = iceServers
            it.bundlePolicy = config.bundlePolicy.toIos()
            it.rtcpMuxPolicy = config.rtcpMuxPolicy.toIos()
            it.iceCandidatePoolSize = config.iceCandidatePoolSize
            it.iceTransportPolicy = config.iceTransportPolicy.toIos()
            it.sdpSemantics = RTCSdpSemantics.RTCSdpSemanticsUnifiedPlan
        }

        val coroutineContext = createConnectionContext(config.exceptionHandler)
        return IosWebRtcConnection(coroutineContext, config) { delegate ->
            localFactory.peerConnectionWithConfiguration(
                constraints = RTCMediaConstraints(),
                configuration = rtcConfig,
                delegate = delegate,
            )
        }
    }
}

/**
 * Factory object for creating iOS WebRTC engine instances.
 */
public object IosWebRtc : WebRtcClientEngineFactory<IosWebRtcEngineConfig> {
    override fun create(block: IosWebRtcEngineConfig.() -> Unit): WebRtcEngine =
        IosWebRtcEngine(IosWebRtcEngineConfig().apply(block))
}
