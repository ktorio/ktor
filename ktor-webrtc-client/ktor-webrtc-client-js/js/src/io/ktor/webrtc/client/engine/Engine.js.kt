/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client.engine

import io.ktor.utils.io.*
import io.ktor.webrtc.client.*
import io.ktor.webrtc.client.peer.*
import io.ktor.webrtc.client.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.w3c.dom.mediacapture.MediaStreamConstraints
import org.w3c.dom.mediacapture.MediaStreamTrack
import kotlin.coroutines.CoroutineContext

public object NavigatorMediaDevices : MediaTrackFactory {
    override suspend fun createAudioTrack(constraints: AudioTrackConstraints): WebRTCAudioTrack {
        val streamConstrains = MediaStreamConstraints(audio = constraints.toJS())
        val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await()
        return JsAudioTrack(mediaStream.getAudioTracks()[0])
    }

    override suspend fun createVideoTrack(constraints: VideoTrackConstraints): WebRTCVideoTrack {
        val streamConstrains = MediaStreamConstraints(audio = constraints.toJS())
        val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await()
        return JsVideoTrack(mediaStream.getVideoTracks()[0])
    }
}

public class JsWebRTCEngine(
    override val config: JsWebRTCEngineConfig,
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: NavigatorMediaDevices
) : WebRTCEngineBase("js-webrtc"), MediaTrackFactory by mediaTrackFactory {
    /**
     * Creates a new WebRTC peer connection with the specified configuration.
     * @param config The WebRTC configuration.
     * @return The WebRTC peer connection.
     */
    override suspend fun createPeerConnection(): WebRtcPeerConnection {
        val rtcConfig: RTCConfiguration = js("{}")
        rtcConfig.iceServers = buildIceServers().toTypedArray()

        val peerConnection = RTCPeerConnection(rtcConfig)
        return JsWebRtcPeerConnection(peerConnection, coroutineContext, config.statsRefreshRate)
    }

    private fun buildIceServers() = config.iceServers.map { iceServer ->
        val rtcIceServer: RTCIceServer = js("{}")
        rtcIceServer.apply {
            urls = iceServer.urls
            username = iceServer.username
            credential = iceServer.credential
        }
    }
}

/**
 * WebRTC peer connection implementation for a Wasm JavaScript platform.
 * @param nativePeerConnection The native RTCPeerConnection object.
 */
public class JsWebRtcPeerConnection(
    private val nativePeerConnection: RTCPeerConnection,
    override val coroutineContext: CoroutineContext,
    private val statsRefreshRate: Long,
) : CoroutineScope, WebRtcPeerConnection {
    private val _iceCandidateFlow = MutableSharedFlow<WebRtcPeerConnection.IceCandidate>()
    override val iceCandidateFlow: SharedFlow<WebRtcPeerConnection.IceCandidate> = _iceCandidateFlow.asSharedFlow()

    private val _statsFlow = MutableStateFlow(listOf<WebRTCStats>())
    override val statsFlow: StateFlow<List<WebRTCStats>> = _statsFlow.asStateFlow()

    init {
        nativePeerConnection.onicecandidate = { event: RTCPeerConnectionIceEvent ->
            event.candidate?.let { candidate ->
                launch {
                    _iceCandidateFlow.emit(
                        WebRtcPeerConnection.IceCandidate(
                            candidate = candidate.candidate,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex?.toInt()
                        )
                    )
                }
            }
        }

        // TODO: subscribe to connection state changes

        // Set up statistics collection
        if (statsRefreshRate > 0) {
            launch {
                while (true) {
                    delay(statsRefreshRate)
                    val stats = nativePeerConnection.getStats().await().toCommon()
                    _statsFlow.emit(stats)
                }
            }
        }
    }

    override fun getNativeConnection(): Any? = nativePeerConnection

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
        nativePeerConnection.addTrack(mediaTrack)
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

public abstract class JsMediaTrack(
    public val nativeTrack: MediaStreamTrack
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

public class JsAudioTrack(nativeTrack: MediaStreamTrack) : WebRTCAudioTrack, JsMediaTrack(nativeTrack)

public class JsVideoTrack(nativeTrack: MediaStreamTrack) : WebRTCVideoTrack, JsMediaTrack(nativeTrack)


@OptIn(InternalAPI::class)
public actual object JsWebRTC : WebRTCClientEngineFactory<JsWebRTCEngineConfig> {
    actual override fun create(block: JsWebRTCEngineConfig.() -> Unit): WebRTCEngine =
        JsWebRTCEngine(JsWebRTCEngineConfig().apply(block))
}
