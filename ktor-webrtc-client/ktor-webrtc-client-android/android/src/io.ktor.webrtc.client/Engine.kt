/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package main.io.ktor.webrtc.client

import android.content.Context
import io.getstream.webrtc.android.StreamWebRtcClient
import io.getstream.webrtc.android.audio.AudioTrack
import io.getstream.webrtc.android.video.VideoTrack
import io.getstream.webrtc.android.model.RTCIceCandidate
import io.getstream.webrtc.android.model.RTCIceServer
import io.getstream.webrtc.android.model.RTCConfiguration
import io.getstream.webrtc.android.model.RTCSessionDescription
import io.getstream.webrtc.android.model.RTCSessionDescription.Type as StreamSessionDescriptionType
import io.getstream.webrtc.android.stats.RTCStatsReport
import io.ktor.webrtc.client.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.EglBase

class AndroidWebRTCEngine(
    private val context: Context,
    private val eglBase: EglBase = EglBase.create()
) : WebRTCEngine {
    private val streamWebRtcClient = StreamWebRtcClient(context, eglBase.eglBaseContext)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override suspend fun createPeerConnection(config: WebRTCConfig): WebRtcPeerConnection {
        val rtcConfiguration = RTCConfiguration(
            iceServers = buildIceServers(config),
            sdpSemantics = RTCConfiguration.SdpSemantics.UNIFIED_PLAN
        )
        val peerConnection = streamWebRtcClient.createPeerConnection(rtcConfiguration)
        return AndroidWebRtcPeerConnection(peerConnection, scope)
    }

    override suspend fun createAudioTrack(config: WebRTCConfig): WebRtcMediaTrack {
        val audioSource = streamWebRtcClient.createAudioSource()
        val audioTrack = streamWebRtcClient.createAudioTrack(audioSource)
        return AndroidWebRtcMediaTrack(audioTrack, AndroidWebRTCAudioSource(audioSource))
    }

    override suspend fun createVideoTrack(config: WebRTCConfig): WebRtcMediaTrack {
        val videoSource = streamWebRtcClient.createVideoSource(false)
        val videoTrack = streamWebRtcClient.createVideoTrack(videoSource)
        return AndroidWebRtcMediaTrack(videoTrack, AndroidWebRTCVideoSource(videoSource))
    }

    private fun buildIceServers(config: WebRTCConfig): List<RTCIceServer> {
        val iceServers = mutableListOf<RTCIceServer>()

        // Add STUN servers
        config.stunServers.forEach { stunServer ->
            iceServers.add(RTCIceServer(urls = listOf(stunServer)))
        }

        // Add TURN servers
        config.turnServers.forEach { turnServer ->
            iceServers.add(
                RTCIceServer(
                    urls = listOf(turnServer.url),
                    username = turnServer.username,
                    credential = turnServer.credential
                )
            )
        }

        return iceServers
    }
}

class AndroidWebRtcPeerConnection(
    private val nativePeerConnection: io.getstream.webrtc.android.PeerConnection,
    private val scope: CoroutineScope
) : WebRtcPeerConnection {
    private val _iceCandidateFlow = MutableSharedFlow<WebRtcPeerConnection.IceCandidate>(replay = 0)
    override val iceCandidateFlow: SharedFlow<WebRtcPeerConnection.IceCandidate> = _iceCandidateFlow.asSharedFlow()

    private val _trackFlow = MutableStateFlow<WebRTCStats>(EmptyWebRTCStats())
    override val trackFlow: StateFlow<WebRTCStats> = _trackFlow.asStateFlow()

    init {
        nativePeerConnection.setIceCandidateListener { iceCandidate ->
            scope.launch {
                _iceCandidateFlow.emit(
                    WebRtcPeerConnection.IceCandidate(
                        candidate = iceCandidate.sdp,
                        sdpMid = iceCandidate.sdpMid,
                        sdpMLineIndex = iceCandidate.sdpMLineIndex
                    )
                )
            }
        }

        nativePeerConnection.setStatsListener { statsReport ->
            scope.launch {
                _trackFlow.emit(AndroidWebRTCStats(statsReport))
            }
        }
    }

    override suspend fun createOffer(): WebRtcPeerConnection.SessionDescription {
        val offer = nativePeerConnection.createOffer()
        return WebRtcPeerConnection.SessionDescription(
            type = WebRtcPeerConnection.SessionDescriptionType.OFFER,
            sdp = offer.sdp
        )
    }

    override suspend fun createAnswer(): WebRtcPeerConnection.SessionDescription {
        val answer = nativePeerConnection.createAnswer()
        return WebRtcPeerConnection.SessionDescription(
            type = WebRtcPeerConnection.SessionDescriptionType.ANSWER,
            sdp = answer.sdp
        )
    }

    override suspend fun setLocalDescription(description: WebRtcPeerConnection.SessionDescription) {
        val type = when (description.type) {
            WebRtcPeerConnection.SessionDescriptionType.OFFER -> StreamSessionDescriptionType.OFFER
            WebRtcPeerConnection.SessionDescriptionType.ANSWER -> StreamSessionDescriptionType.ANSWER
        }

        val rtcSessionDescription = RTCSessionDescription(
            type = type,
            sdp = description.sdp
        )

        nativePeerConnection.setLocalDescription(rtcSessionDescription)
    }

    override suspend fun setRemoteDescription(description: WebRtcPeerConnection.SessionDescription) {
        val type = when (description.type) {
            WebRtcPeerConnection.SessionDescriptionType.OFFER -> StreamSessionDescriptionType.OFFER
            WebRtcPeerConnection.SessionDescriptionType.ANSWER -> StreamSessionDescriptionType.ANSWER
        }

        val rtcSessionDescription = RTCSessionDescription(
            type = type,
            sdp = description.sdp
        )

        nativePeerConnection.setRemoteDescription(rtcSessionDescription)
    }

    override suspend fun addIceCandidate(candidate: WebRtcPeerConnection.IceCandidate) {
        val rtcIceCandidate = RTCIceCandidate(
            sdp = candidate.candidate,
            sdpMid = candidate.sdpMid,
            sdpMLineIndex = candidate.sdpMLineIndex ?: 0
        )

        nativePeerConnection.addIceCandidate(rtcIceCandidate)
    }

    override suspend fun addTrack(track: WebRtcMediaTrack) {
        if (track is AndroidWebRtcMediaTrack) {
            if (track.nativeTrack is AudioTrack) {
                nativePeerConnection.addTrack(track.nativeTrack)
            } else if (track.nativeTrack is VideoTrack) {
                nativePeerConnection.addTrack(track.nativeTrack)
            }
        }
    }

    override suspend fun removeTrack(track: WebRtcMediaTrack) {
        if (track is AndroidWebRtcMediaTrack) {
            if (track.nativeTrack is AudioTrack) {
                nativePeerConnection.removeTrack(track.nativeTrack)
            } else if (track.nativeTrack is VideoTrack) {
                nativePeerConnection.removeTrack(track.nativeTrack)
            }
        }
    }

    override suspend fun close() {
        nativePeerConnection.close()
    }
}

class AndroidWebRTCStats(private val statsReport: RTCStatsReport) : WebRTCStats {
    override val id: String? = statsReport.statsMap.keys.firstOrNull()
    override val type: String? = "rtc-stats"
    override val timestampUs: Long = System.currentTimeMillis() * 1000

    override val members: Map<String, Any> = statsReport.statsMap.mapValues { (_, value) ->
        value.members
    }
}

class EmptyWebRTCStats : WebRTCStats {
    override val id: String? = null
    override val type: String? = null
    override val timestampUs: Long = 0
    override val members: Map<String, Any> = emptyMap()
}

class AndroidWebRTCAudioSource(
    private val nativeSource: io.getstream.webrtc.android.audio.AudioSource
) : WebRTCAudioSource

class AndroidWebRTCVideoSource(
    private val nativeSource: io.getstream.webrtc.android.video.VideoSource
) : WebRTCVideoSource {
    override val isScreencast: Boolean
        get() = nativeSource.isScreencast()
}

class AndroidWebRtcMediaTrack(
    val nativeTrack: Any, // AudioTrack or VideoTrack
    override val src: WebRTCMediaSource
) : WebRtcMediaTrack {
    override val id: String = when (nativeTrack) {
        is AudioTrack -> nativeTrack.id()
        is VideoTrack -> nativeTrack.id()
        else -> throw IllegalArgumentException("Unsupported track type")
    }
}
