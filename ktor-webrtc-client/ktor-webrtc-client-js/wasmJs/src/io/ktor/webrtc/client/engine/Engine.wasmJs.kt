/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client.engine

import io.ktor.utils.io.InternalAPI
import io.ktor.webrtc.client.*
import io.ktor.webrtc.client.peer.*
import io.ktor.webrtc.client.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.w3c.dom.mediacapture.MediaStream
import org.w3c.dom.mediacapture.MediaStreamTrack
import kotlin.coroutines.CoroutineContext

public class WasmJsWebRTCEngine(override val config: JsWebRTCEngineConfig) : WebRTCEngineBase("ktor-webrtc-wasm-js") {

    /**
     * Creates a new WebRTC peer connection with the specified configuration.
     * @param config The WebRTC configuration.
     * @return The WebRTC peer connection.
     */
    override suspend fun createPeerConnection(): WebRtcPeerConnection {
        val rtcConfig = makeEmptyObject<RTCConfiguration>()
        rtcConfig.iceServers = mapIceServers(config.iceServers + config.turnServers)

        val peerConnection = RTCPeerConnection(rtcConfig)
        return WasmJsWebRtcPeerConnection(peerConnection, coroutineContext, config.statsRefreshRate)
    }

    /**
     * Creates an audio track.
     * @return The WebRTC media track.
     */
    override suspend fun createAudioTrack(): WebRTCMediaTrack {
        val constraints = audioEnabledConstraints()
        val mediaStream = navigator.mediaDevices.getUserMedia(constraints).await<MediaStream>()
        val audioTrack = mediaStream.getAudioTracks()[0]!!
        return WasmJsMediaTrack(audioTrack)
    }

    /**
     * Creates a video track.
     * @return The WebRTC media track.
     */
    override suspend fun createVideoTrack(): WebRTCMediaTrack {
        val constraints = videoEnabledConstraints()
        val mediaStream = navigator.mediaDevices.getUserMedia(constraints).await<MediaStream>()
        val videoTrack = mediaStream.getVideoTracks()[0]!!
        return WasmJsMediaTrack(videoTrack)
    }
}

/**
 * WebRTC peer connection implementation for a Wasm JavaScript platform.
 * @param nativePeerConnection The native RTCPeerConnection object.
 */
public class WasmJsWebRtcPeerConnection(
    private val nativePeerConnection: RTCPeerConnection,
    override val coroutineContext: CoroutineContext,
    private val statsRefreshRate: Long,
) : WebRtcPeerConnection, CoroutineScope {
    private val _iceCandidateFlow = MutableSharedFlow<WebRtcPeerConnection.IceCandidate>(replay = 0)
    override val iceCandidateFlow: SharedFlow<WebRtcPeerConnection.IceCandidate> = _iceCandidateFlow.asSharedFlow()

    private val _statsFlow = MutableStateFlow<WebRTCStatsReport>(EmptyWebRTCStatsReport)
    override val statsFlow: StateFlow<WebRTCStatsReport> = _statsFlow.asStateFlow()

    init {
        nativePeerConnection.onicecandidate = { conn: RTCPeerConnection, event: RTCPeerConnectionIceEvent ->
            launch {
                event.candidate?.let { candidate ->
                    _iceCandidateFlow.emit(
                        WebRtcPeerConnection.IceCandidate(
                            candidate = candidate.candidate,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex?.toInt()
                        )
                    )
                }
            }
            null
        }

        // Set up statistics collection
        if (statsRefreshRate > 0) {
            launch {
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
        nativePeerConnection.setLocalDescription(description.toJS()).await<JsUndefined>()
    }

    override suspend fun setRemoteDescription(description: WebRtcPeerConnection.SessionDescription) {
        nativePeerConnection.setRemoteDescription(description.toJS()).await<JsUndefined>()
    }

    override suspend fun addIceCandidate(candidate: WebRtcPeerConnection.IceCandidate) {
        nativePeerConnection.addIceCandidate(candidate.toJS()).await<JsUndefined>()
    }

    override suspend fun addTrack(track: WebRTCMediaTrack) {
        val mediaTrack = (track as WasmJsMediaTrack).nativeTrack
        val stream = MediaStream()
        stream.addTrack(mediaTrack)
        nativePeerConnection.addTrack(mediaTrack, stream)
    }

    override suspend fun removeTrack(track: WebRTCMediaTrack) {
        val mediaTrack = (track as WasmJsMediaTrack).nativeTrack
        val sender = nativePeerConnection.getSenders().toArray().find { it.track == mediaTrack }
        sender?.let { nativePeerConnection.removeTrack(it) }
    }

    override fun close() {
        nativePeerConnection.close()
    }
}

public class WasmJsMediaTrack(
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

@OptIn(InternalAPI::class)
public actual object JsWebRTC : WebRTCClientEngineFactory<JsWebRTCEngineConfig> {
    init {
        DefaultWebRTCEngine.factory = this
    }

    actual override fun create(block: JsWebRTCEngineConfig.() -> Unit): WebRTCEngine =
        WasmJsWebRTCEngine(JsWebRTCEngineConfig().apply(block))
}
