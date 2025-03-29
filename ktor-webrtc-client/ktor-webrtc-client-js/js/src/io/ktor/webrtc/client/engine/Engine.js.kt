/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client.engine

import io.ktor.webrtc.client.EmptyWebRTCStatsReport
import io.ktor.webrtc.client.WebRTCClientEngineFactory
import io.ktor.webrtc.client.WebRTCConfig
import io.ktor.webrtc.client.WebRTCEngine
import io.ktor.webrtc.client.WebRTCEngineBase
import io.ktor.webrtc.client.WebRTCMediaTrack
import io.ktor.webrtc.client.WebRTCStatsReport
import io.ktor.webrtc.client.WebRtcPeerConnection
import io.ktor.webrtc.client.peer.*
import io.ktor.webrtc.client.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.w3c.dom.mediacapture.MediaStream
import org.w3c.dom.mediacapture.MediaStreamTrack

public class JsWebRTCEngine(override val config: JsWebRTCEngineConfig) : WebRTCEngineBase("js-webrtc") {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Creates a new WebRTC peer connection with the specified configuration.
     * @param config The WebRTC configuration.
     * @return The WebRTC peer connection.
     */
    override suspend fun createPeerConnection(): WebRtcPeerConnection {
        val rtcConfig: RTCConfiguration = js("{}")
        rtcConfig.iceServers = buildIceServers(config).toTypedArray()

        val peerConnection = RTCPeerConnection(rtcConfig)
        return JsWebRtcPeerConnection(peerConnection, scope, config.statsRefreshRate)
    }

    /**
     * Creates an audio track.
     * @return The WebRTC media track.
     */
    override suspend fun createAudioTrack(): WebRTCMediaTrack {
        val constraints = js("{ audio: true }")
        val mediaStream = navigator.mediaDevices.getUserMedia(constraints).await<MediaStream>()
        val audioTrack = mediaStream.getAudioTracks()[0]
        return JsMediaTrack(audioTrack)
    }

    /**
     * Creates a video track.
     * @return The WebRTC media track.
     */
    override suspend fun createVideoTrack(): WebRTCMediaTrack {
        val constraints = js("{ video: true }")
        val mediaStream = navigator.mediaDevices.getUserMedia(constraints).await<MediaStream>()
        val videoTrack = mediaStream.getVideoTracks()[0]
        return JsMediaTrack(videoTrack)
    }

    /**
     * Converts the WebRTC configuration to a list of RTCIceServer objects.
     * @param config The WebRTC configuration.
     * @return A list of RTCIceServer objects.
     */
    private fun buildIceServers(config: WebRTCConfig): List<RTCIceServer> {
        return config.iceServers.map { iceServer ->
            js("{ urls: iceServer.urls, username: iceServer.username, credential: iceServer.credential }")
        }
    }
}

/**
 * WebRTC peer connection implementation for a Wasm JavaScript platform.
 * @param nativePeerConnection The native RTCPeerConnection object.
 * @param scope The coroutine scope for this connection.
 */
public class JsWebRtcPeerConnection(
    private val nativePeerConnection: RTCPeerConnection,
    private val scope: CoroutineScope,
    private val statsRefreshRate: Long
) : WebRtcPeerConnection {
    private val _iceCandidateFlow = MutableSharedFlow<WebRtcPeerConnection.IceCandidate>(replay = 0)
    override val iceCandidateFlow: SharedFlow<WebRtcPeerConnection.IceCandidate> = _iceCandidateFlow.asSharedFlow()

    private val _statsFlow = MutableStateFlow<WebRTCStatsReport>(EmptyWebRTCStatsReport)
    override val statsFlow: StateFlow<WebRTCStatsReport> = _statsFlow.asStateFlow()

    init {
        nativePeerConnection.onicecandidate = { conn: RTCPeerConnection, event: RTCPeerConnectionIceEvent ->
            event.candidate?.let { candidate ->
                scope.launch {
                    _iceCandidateFlow.emit(
                        WebRtcPeerConnection.IceCandidate(
                            candidate = candidate.candidate,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex?.toInt()
                        )
                    )
                }
            }
            Unit
        }

        // Set up statistics collection
        if (statsRefreshRate > 0) {
            scope.launch {
                while (true) {
                    delay(statsRefreshRate)
                    _statsFlow.emit(nativePeerConnection.getStats().await<RTCStatsReport>().toCommon())
                }
            }
        }
    }

    override suspend fun createOffer(): WebRtcPeerConnection.SessionDescription {
        return nativePeerConnection.createOffer().await<RTCSessionDescriptionInit>().toCommon()
    }

    override suspend fun createAnswer(): WebRtcPeerConnection.SessionDescription {
        return nativePeerConnection.createAnswer().await<RTCSessionDescriptionInit>().toCommon()
    }

    override suspend fun setLocalDescription(description: WebRtcPeerConnection.SessionDescription) {
        nativePeerConnection.setLocalDescription(description.toJS()).await()
    }

    override suspend fun setRemoteDescription(description: WebRtcPeerConnection.SessionDescription) {
        nativePeerConnection.setRemoteDescription(description.toJS()).await()
    }

    override suspend fun addIceCandidate(candidate: WebRtcPeerConnection.IceCandidate) {
        nativePeerConnection.addIceCandidate(candidate.toJS()).await()
    }

    override suspend fun addTrack(track: WebRTCMediaTrack) {
        val mediaTrack = (track as JsMediaTrack).nativeTrack
        val stream = MediaStream()
        stream.addTrack(mediaTrack)
        nativePeerConnection.addTrack(mediaTrack, stream)
    }

    override suspend fun removeTrack(track: WebRTCMediaTrack) {
        val mediaTrack = (track as JsMediaTrack).nativeTrack
        val sender = nativePeerConnection.getSenders().find { it.track == mediaTrack }
        sender?.let { nativePeerConnection.removeTrack(it) }
    }

    override fun close() {
        nativePeerConnection.close()
    }
}

public class JsMediaTrack(
    internal val nativeTrack: MediaStreamTrack
) : WebRTCMediaTrack {
    public override val id: String = nativeTrack.id
    public override val kind: WebRTCMediaTrack.Type = when (nativeTrack.kind) {
        "audio" -> WebRTCMediaTrack.Type.AUDIO
        "video" -> WebRTCMediaTrack.Type.VIDEO
        else -> error("Unknown media track kind: ${nativeTrack.kind}")
    }
    public override val enabled: Boolean
        get() = nativeTrack.enabled

    override fun enable(enabled: Boolean) {
        nativeTrack.enabled = enabled
    }

    override fun stop() {
        nativeTrack.stop()
    }
}

public actual object JsWebRTC : WebRTCClientEngineFactory<JsWebRTCEngineConfig> {
    actual override fun create(block: JsWebRTCEngineConfig.() -> Unit): WebRTCEngine =
        JsWebRTCEngine(JsWebRTCEngineConfig().apply(block))
}
