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
import org.w3c.dom.mediacapture.MediaStream
import org.w3c.dom.mediacapture.MediaStreamConstraints
import org.w3c.dom.mediacapture.MediaStreamTrack
import kotlin.collections.List
import kotlin.coroutines.CoroutineContext

public object NavigatorMediaDevices : MediaTrackFactory {
    override suspend fun createAudioTrack(constraints: WebRTCMedia.AudioTrackConstraints): WebRTCMedia.AudioTrack {
        val streamConstraints = MediaStreamConstraints(audio = constraints.toJS())
        val mediaStream = navigator.mediaDevices.getUserMedia(streamConstraints).await<MediaStream>()
        return WasmJsAudioTrack(mediaStream.getAudioTracks()[0]!!)
    }

    override suspend fun createVideoTrack(constraints: WebRTCMedia.VideoTrackConstraints): WebRTCMedia.VideoTrack {
        val streamConstraints = MediaStreamConstraints(video = constraints.toJS())
        val mediaStream = navigator.mediaDevices.getUserMedia(streamConstraints).await<MediaStream>()
        return WasmJsVideoTrack(mediaStream.getVideoTracks()[0]!!)
    }
}

public class WasmJsWebRTCEngine(
    override val config: JsWebRTCEngineConfig,
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: NavigatorMediaDevices
) : WebRTCEngineBase("ktor-webrtc-wasm-js"), MediaTrackFactory by mediaTrackFactory {

    /**
     * Creates a new WebRTC peer connection with the specified configuration.
     */
    override suspend fun createPeerConnection(): WebRtcPeerConnection {
        val rtcConfig = jsObject<RTCConfiguration> {
            iceServers = mapIceServers(config.iceServers + config.turnServers)
        }
        val peerConnection = RTCPeerConnection(rtcConfig)
        return WasmJsWebRtcPeerConnection(peerConnection, coroutineContext, config.statsRefreshRate)
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
    private val _iceCandidateFlow = MutableSharedFlow<WebRTC.IceCandidate>(replay = 0)
    override val iceCandidateFlow: SharedFlow<WebRTC.IceCandidate> = _iceCandidateFlow.asSharedFlow()

    private val _statsFlow = MutableStateFlow(emptyList<WebRTC.Stats>())
    override val statsFlow: StateFlow<List<WebRTC.Stats>> = _statsFlow.asStateFlow()

    init {
        nativePeerConnection.onicecandidate = { event: RTCPeerConnectionIceEvent ->
            event.candidate?.let { candidate ->
                launch {
                    _iceCandidateFlow.emit(
                        WebRTC.IceCandidate(
                            sdpMid = candidate.sdpMid.toString(),
                            candidate = candidate.candidate.toString(),
                            sdpMLineIndex = candidate.sdpMLineIndex.toInt()
                        )
                    )
                }
            }
        }

        // Set up statistics collection
        if (statsRefreshRate > 0) {
            launch {
                while (true) {
                    delay(statsRefreshRate)
                    val stats = nativePeerConnection.getStats().await<RTCStatsReport>()
                    _statsFlow.emit(stats.toCommon())
                }
            }
        }
    }

    override fun getNativeConnection(): Any = nativePeerConnection

    override suspend fun createOffer(): WebRTC.SessionDescription {
        return nativePeerConnection.createOffer().await<RTCSessionDescriptionInit>().toCommon()
    }

    override suspend fun createAnswer(): WebRTC.SessionDescription {
        return nativePeerConnection.createAnswer().await<RTCSessionDescriptionInit>().toCommon()
    }

    override suspend fun setLocalDescription(description: WebRTC.SessionDescription) {
        nativePeerConnection.setLocalDescription(description.toJS()).await<JsUndefined>()
    }

    override suspend fun setRemoteDescription(description: WebRTC.SessionDescription) {
        nativePeerConnection.setRemoteDescription(description.toJS()).await<JsUndefined>()
    }

    override suspend fun addIceCandidate(candidate: WebRTC.IceCandidate) {
        nativePeerConnection.addIceCandidate(candidate.toJS()).await<JsUndefined>()
    }

    override suspend fun addTrack(track: WebRTCMedia.Track): WebRTC.RtpSender {
        val mediaTrack = (track as WasmJsMediaTrack).nativeTrack
        val stream = MediaStream().apply { addTrack(mediaTrack) }
        return WasmJsRtpSender(nativePeerConnection.addTrack(mediaTrack, stream))
    }

    override suspend fun removeTrack(track: WebRTCMedia.Track) {
        val mediaTrack = (track as WasmJsMediaTrack).nativeTrack
        val sender = nativePeerConnection.getSenders().toArray().find { it.track == mediaTrack }
        sender?.let { nativePeerConnection.removeTrack(it) }
    }

    override fun close() {
        nativePeerConnection.close()
    }

    override suspend fun removeTrack(sender: WebRTC.RtpSender) {
        return nativePeerConnection.removeTrack((sender as WasmJsRtpSender).nativeSender)
    }
}

public abstract class WasmJsMediaTrack(
    internal val nativeTrack: MediaStreamTrack
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
        public fun from(nativeTrack: MediaStreamTrack): WasmJsMediaTrack = when (nativeTrack.kind.toTrackKind()) {
            WebRTCMedia.TrackType.AUDIO -> WasmJsAudioTrack(nativeTrack)
            WebRTCMedia.TrackType.VIDEO -> WasmJsVideoTrack(nativeTrack)
        }
    }
}

public class WasmJsAudioTrack(nativeTrack: MediaStreamTrack) : WebRTCMedia.AudioTrack, WasmJsMediaTrack(nativeTrack)

public class WasmJsVideoTrack(nativeTrack: MediaStreamTrack) : WebRTCMedia.VideoTrack, WasmJsMediaTrack(nativeTrack)

public class WasmJsRtpSender(public val nativeSender: RTCRtpSender) : WebRTC.RtpSender {
    override val dtmf: WebRTC.DtmfSender? get() = nativeSender.dtmf?.let { WasmJsDtmfSender(it) }

    override val track: WebRTCMedia.Track? get() = nativeSender.track?.let { WasmJsMediaTrack.from(it) }

    override suspend fun replaceTrack(withTrack: WebRTCMedia.Track?) {
        nativeSender.replaceTrack((withTrack as? WasmJsMediaTrack)?.nativeTrack)
    }

    override suspend fun getParameters(): WebRTC.RtpParameters {
        val params = nativeSender.getParameters()?.unsafeCast<RTCRtpSendParameters>() ?: error("Params are undefined")
        return WasmJsRtpParameters(params)
    }

    override suspend fun setParameters(parameters: WebRTC.RtpParameters) {
        if (parameters is WasmJsRtpParameters) {
            nativeSender.setParameters(parameters.nativeRtpParameters).await<JsUndefined>()
        }
    }
}

public class WasmJsDtmfSender(public val nativeSender: RTCDTMFSender) : WebRTC.DtmfSender {
    override val toneBuffer: String
        get() = nativeSender.toneBuffer.toString()
    override val canInsertDTMF: Boolean
        get() = nativeSender.canInsertDTMF.toBoolean()

    override fun insertDTMF(tones: String, duration: Int, interToneGap: Int) {
        nativeSender.insertDTMF(tones.toJsString(), duration.toJsNumber(), interToneGap.toJsNumber())
    }
}

public class WasmJsRtpParameters(public val nativeRtpParameters: RTCRtpSendParameters) : WebRTC.RtpParameters {
    override val transactionId: String = nativeRtpParameters.transactionId.toString()
    override val encodings: Iterable<RTCRtpEncodingParameters> = nativeRtpParameters.encodings.toArray().asIterable()
    override val codecs: Iterable<RTCRtpCodecParameters> = nativeRtpParameters.codecs.toArray().asIterable()
    override val rtcp: RTCRtcpParameters = nativeRtpParameters.rtcp

    override val headerExtensions: List<WebRTC.RtpHeaderExtensionParameters>
        get() = nativeRtpParameters.headerExtensions.toArray().map {
            WebRTC.RtpHeaderExtensionParameters(
                it.id.toInt(),
                it.uri.toString(),
                it.encrypted ?: false
            )
        }

    override val degradationPreference: WebRTC.DegradationPreference
        get() = nativeRtpParameters.degradationPreference?.toString().toDegradationPreference()
}

public actual object JsWebRTC : WebRTCClientEngineFactory<JsWebRTCEngineConfig> {
    actual override fun create(block: JsWebRTCEngineConfig.() -> Unit): WebRTCEngine =
        WasmJsWebRTCEngine(JsWebRTCEngineConfig().apply(block))
}
