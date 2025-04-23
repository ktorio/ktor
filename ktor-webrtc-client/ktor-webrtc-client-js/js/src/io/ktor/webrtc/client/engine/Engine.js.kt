/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client.engine

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
    override suspend fun createAudioTrack(constraints: WebRTCMedia.AudioTrackConstraints): WebRTCMedia.AudioTrack {
        val streamConstrains = MediaStreamConstraints(audio = constraints.toJS())
        val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await()
        return JsAudioTrack(mediaStream.getAudioTracks()[0])
    }

    override suspend fun createVideoTrack(constraints: WebRTCMedia.VideoTrackConstraints): WebRTCMedia.VideoTrack {
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
     * @return The WebRTC peer connection.
     */
    override suspend fun createPeerConnection(): WebRtcPeerConnection {
        val rtcConfig = jsObject<RTCConfiguration> {
            iceServers = buildIceServers().toTypedArray()
        }
        val peerConnection = RTCPeerConnection(rtcConfig)
        return JsWebRtcPeerConnection(peerConnection, coroutineContext, config.statsRefreshRate)
    }

    private fun buildIceServers() = config.iceServers.map { iceServer ->
        jsObject<RTCIceServer> {
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
    private val _iceCandidateFlow = MutableSharedFlow<WebRTC.IceCandidate>()
    override val iceCandidateFlow: SharedFlow<WebRTC.IceCandidate> = _iceCandidateFlow.asSharedFlow()

    private val _statsFlow = MutableStateFlow(listOf<WebRTC.Stats>())
    override val statsFlow: StateFlow<List<WebRTC.Stats>> = _statsFlow.asStateFlow()

    init {
        nativePeerConnection.onicecandidate = { event: RTCPeerConnectionIceEvent ->
            event.candidate?.let { candidate ->
                launch {
                    _iceCandidateFlow.emit(
                        WebRTC.IceCandidate(
                            candidate = candidate.candidate,
                            sdpMid = candidate.sdpMid!!,
                            sdpMLineIndex = candidate.sdpMLineIndex?.toInt()!!
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

    override fun getNativeConnection(): Any = nativePeerConnection

    override suspend fun createOffer(): WebRTC.SessionDescription {
        return nativePeerConnection.createOffer().await().toCommon()
    }

    override suspend fun createAnswer(): WebRTC.SessionDescription {
        return nativePeerConnection.createAnswer().await().toCommon()
    }

    override suspend fun setLocalDescription(description: WebRTC.SessionDescription) {
        nativePeerConnection.setLocalDescription(description.toJS()).await()
    }

    override suspend fun setRemoteDescription(description: WebRTC.SessionDescription) {
        nativePeerConnection.setRemoteDescription(description.toJS()).await()
    }

    override suspend fun addIceCandidate(candidate: WebRTC.IceCandidate) {
        nativePeerConnection.addIceCandidate(candidate.toJS()).await()
    }

    override suspend fun addTrack(track: WebRTCMedia.Track): WebRTC.RtpSender {
        val mediaTrack = (track as JsMediaTrack).nativeTrack
        return JsRtpSender(nativePeerConnection.addTrack(mediaTrack))
    }

    override suspend fun removeTrack(track: WebRTCMedia.Track) {
        val mediaTrack = (track as JsMediaTrack).nativeTrack
        val sender = nativePeerConnection.getSenders().find { it.track == mediaTrack }
        sender?.let { nativePeerConnection.removeTrack(it) }
    }

    override suspend fun removeTrack(sender: WebRTC.RtpSender) {
        val rtpSender = (sender as JsRtpSender).nativeSender
        nativePeerConnection.removeTrack(rtpSender)
    }

    override fun close() {
        nativePeerConnection.close()
    }
}

public abstract class JsMediaTrack(
    public val nativeTrack: MediaStreamTrack
) : WebRTCMedia.Track {
    public override val id: String = nativeTrack.id
    public override val kind: WebRTCMedia.TrackType = nativeTrack.kind.toTrackKind()

    public override val enabled: Boolean
        get() = nativeTrack.enabled

    override fun enable(enabled: Boolean) {
        nativeTrack.enabled = enabled
    }

    override fun close() {
        nativeTrack.stop()
    }

    public companion object {
        public fun from(nativeTrack: MediaStreamTrack): JsMediaTrack = when (nativeTrack.kind.toTrackKind()) {
            WebRTCMedia.TrackType.AUDIO -> JsAudioTrack(nativeTrack)
            WebRTCMedia.TrackType.VIDEO -> JsVideoTrack(nativeTrack)
        }
    }
}

public class JsAudioTrack(nativeTrack: MediaStreamTrack) : WebRTCMedia.AudioTrack, JsMediaTrack(nativeTrack)

public class JsVideoTrack(nativeTrack: MediaStreamTrack) : WebRTCMedia.VideoTrack, JsMediaTrack(nativeTrack)

public class JsRtpSender(public val nativeSender: RTCRtpSender) : WebRTC.RtpSender {
    override val dtmf: WebRTC.DtmfSender? get() = nativeSender.dtmf?.let { JsDtmfSender(it) }

    override val track: WebRTCMedia.Track? get() = nativeSender.track?.let { JsMediaTrack.from(it) }

    override suspend fun replaceTrack(withTrack: WebRTCMedia.Track?) {
        nativeSender.replaceTrack((withTrack as? JsMediaTrack)?.nativeTrack)
    }

    override suspend fun getParameters(): WebRTC.RtpParameters {
        return JsRtpParameters(nativeSender.getParameters().unsafeCast<RTCRtpSendParameters>())
    }

    override suspend fun setParameters(parameters: WebRTC.RtpParameters) {
        (parameters as? JsRtpParameters)?.let { nativeSender.setParameters(it.nativeRtpParameters).await() }
    }
}

public class JsDtmfSender(public val nativeSender: RTCDTMFSender) : WebRTC.DtmfSender {
    override val toneBuffer: String
        get() = nativeSender.toneBuffer
    override val canInsertDTMF: Boolean
        get() = nativeSender.canInsertDTMF

    override fun insertDTMF(tones: String, duration: Int, interToneGap: Int) {
        nativeSender.insertDTMF(tones, duration, interToneGap)
    }
}

public class JsRtpParameters(public val nativeRtpParameters: RTCRtpSendParameters) : WebRTC.RtpParameters {
    override val transactionId: String = nativeRtpParameters.transactionId
    override val encodings: Iterable<RTCRtpEncodingParameters> = nativeRtpParameters.encodings.asIterable()
    override val codecs: Iterable<RTCRtpCodecParameters> = nativeRtpParameters.codecs.asIterable()
    override val rtcp: RTCRtcpParameters = nativeRtpParameters.rtcp

    override val headerExtensions: List<WebRTC.RtpHeaderExtensionParameters>
        get() = nativeRtpParameters.headerExtensions.map {
            WebRTC.RtpHeaderExtensionParameters(
                it.id.toInt(),
                it.uri,
                it.encrypted ?: false
            )
        }

    override val degradationPreference: WebRTC.DegradationPreference
        get() = nativeRtpParameters.degradationPreference.toDegradationPreference()
}

public actual object JsWebRTC : WebRTCClientEngineFactory<JsWebRTCEngineConfig> {
    actual override fun create(block: JsWebRTCEngineConfig.() -> Unit): WebRTCEngine =
        JsWebRTCEngine(JsWebRTCEngineConfig().apply(block))
}
