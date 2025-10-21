/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import dev.onvoid.webrtc.PeerConnectionFactory
import dev.onvoid.webrtc.RTCConfiguration
import io.ktor.client.webrtc.media.JvmMediaDevices

/**
 * Configuration for the JVM WebRTC engine that extends the base WebRTC configuration.
 *
 * @property rtcFactory Optional custom [PeerConnectionFactory] for creating peer connections.
 * If not provided, the factory from the [MediaTrackFactory] will be used.
 */
public class JvmWebRtcEngineConfig : WebRtcConfig() {
    public var rtcFactory: PeerConnectionFactory? = null
}

/**
 * JVM WebRTC engine implementation that handles peer connection creation and management using
 * dev.onvoid.webrtc bindings.
 *
 * @param config The JVM-specific WebRTC engine configuration
 * @param mediaTrackFactory Factory for creating media tracks, defaults to JvmMediaDevices if not specified in config
 */
public class JvmWebRtcEngine(
    override val config: JvmWebRtcEngineConfig,
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: JvmMediaDevices()
) : WebRtcEngineBase("jvm-webrtc", config), MediaTrackFactory by mediaTrackFactory {

    private val localFactory: PeerConnectionFactory
        get() = config.rtcFactory
            ?: (mediaTrackFactory as? JvmMediaDevices)?.peerConnectionFactory
            ?: error("Provide `JvmWebRtcEngineConfig.rtcFactory` when mediaTrackFactory is not `JvmMediaDevices`.")

    override suspend fun createPeerConnection(config: WebRtcConnectionConfig): WebRtcPeerConnection {
        require(config.iceCandidatePoolSize <= 0) {
            "Candidates prefetching is not supported on current platform."
        }
        val rtcConfig = RTCConfiguration().apply {
            iceTransportPolicy = config.iceTransportPolicy.toJvm()
            iceServers = config.iceServers.map { it.toJvm() }
            rtcpMuxPolicy = config.rtcpMuxPolicy.toJvm()
            bundlePolicy = config.bundlePolicy.toJvm()
        }
        val coroutineContext = createConnectionContext(config.exceptionHandler)
        return JvmWebRtcConnection(coroutineContext, config) { observer ->
            localFactory.createPeerConnection(rtcConfig, observer)
                ?: error("Failed to create peer connection.")
        }
    }
}

/**
 * Factory object for creating JVM WebRTC engine instances.
 */
public object JvmWebRtc : WebRtcClientEngineFactory<JvmWebRtcEngineConfig> {
    override fun create(block: JvmWebRtcEngineConfig.() -> Unit): WebRtcEngine =
        JvmWebRtcEngine(JvmWebRtcEngineConfig().apply(block))
}
