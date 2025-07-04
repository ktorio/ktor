/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import android.content.Context
import io.ktor.client.webrtc.media.*
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnectionFactory

public class AndroidWebRtcEngineConfig : WebRtcConfig() {
    /**
     * Android application context needed to create media tracks.
     * */
    public lateinit var context: Context

    /**
     * In Android WebRtc implementation PeerConnectionFactory is coupled with the MediaTrackFactory, so if you provide a
     * custom MediaTrackFactory, you should specify PeerConnectionFactory to initialize a PeerConnection.
     * */
    public var rtcFactory: PeerConnectionFactory? = null
}

public class AndroidWebRtcEngine(
    override val config: AndroidWebRtcEngineConfig,
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: AndroidMediaDevices(config.context)
) : WebRtcEngineBase("android-webrtc"), MediaTrackFactory by mediaTrackFactory {

    private fun getLocalFactory(): PeerConnectionFactory {
        val factory = config.rtcFactory ?: (mediaTrackFactory as? AndroidMediaDevices)?.peerConnectionFactory
        if (factory == null) {
            error("Please specify custom rtcFactory for custom MediaTrackFactory")
        }
        return factory
    }

    private fun WebRtc.IceServer.toNative(): PeerConnection.IceServer {
        return PeerConnection.IceServer
            .builder(urls)
            .setUsername(username ?: "") // will throw if null
            .setPassword(credential ?: "") // will throw if null
            .createIceServer()
    }

    override suspend fun createPeerConnection(connectionConfig: WebRtcConnectionConfig?): WebRtcPeerConnection {
        val config = connectionConfig ?: WebRtcConnectionConfig().apply(config.defaultConnectionConfig)
        val iceServers = config.iceServers.map { it.toNative() }
        val rtcConfig = RTCConfiguration(iceServers).apply {
            bundlePolicy = config.bundlePolicy.toNative()
            rtcpMuxPolicy = config.rtcpMuxPolicy.toNative()
            iceCandidatePoolSize = config.iceCandidatePoolSize
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = config.iceTransportPolicy.toNative()
        }

        return AndroidWebRtcPeerConnection(coroutineContext, config).initialize { observer ->
            getLocalFactory().createPeerConnection(rtcConfig, observer)
        }
    }
}
