/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.peer.*
import io.ktor.client.webrtc.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.mediacapture.MediaStream
import org.w3c.dom.mediacapture.MediaStreamConstraints
import org.w3c.dom.mediacapture.MediaStreamTrack
import kotlin.coroutines.CoroutineContext

private inline fun <T> withPermissionException(mediaType: String, block: () -> T): T {
    try {
        return block()
    } catch (e: Throwable) {
        if (e.message?.contains("Permission denied") == true) {
            throw WebRTCMedia.PermissionException(mediaType)
        }
        throw e
    }
}

public object NavigatorMediaDevices : MediaTrackFactory {
    override suspend fun createAudioTrack(constraints: WebRTCMedia.AudioTrackConstraints): WebRTCMedia.AudioTrack =
        withPermissionException("audio") {
            val streamConstrains = MediaStreamConstraints(audio = constraints.toJS())
            val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await<MediaStream>()
            return WasmJsAudioTrack(mediaStream.getAudioTracks()[0]!!, mediaStream)
        }

    override suspend fun createVideoTrack(constraints: WebRTCMedia.VideoTrackConstraints): WebRTCMedia.VideoTrack =
        withPermissionException("video") {
            val streamConstrains = MediaStreamConstraints(audio = constraints.toJS())
            val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await<MediaStream>()
            return WasmJsVideoTrack(mediaStream.getVideoTracks()[0]!!, mediaStream)
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
) : WebRtcPeerConnection(), CoroutineScope {
    init {
        nativePeerConnection.onicecandidate = { event: RTCPeerConnectionIceEvent ->
            event.candidate?.let { candidate ->
                launch { iceCandidates.emit(candidate.toCommon()) }
            }
        }

        nativePeerConnection.oniceconnectionstatechange = {
            val newState = nativePeerConnection.iceConnectionState.toString().toIceConnectionState()
            launch { currentIceConnectionState.emit(newState) }
        }

        nativePeerConnection.ontrack = { event: RTCTrackEvent ->
            val stream = event.streams[0]
            stream?.onremovetrack = { e ->
                launch { remoteTracks.emit(Remove(WasmJsMediaTrack.from(e.track, stream!!))) }
            }
            launch {
                remoteTracks.emit(Add(WasmJsMediaTrack.from(event.track, stream ?: MediaStream())))
            }
        }

        // Set up statistics collection
        if (statsRefreshRate > 0) {
            launch {
                while (true) {
                    delay(statsRefreshRate)
                    val stats = nativePeerConnection.getStats().await<RTCStatsReport>()
                    currentStats.emit(stats.toCommon())
                }
            }
        }
    }

    override fun getNativeConnection(): Any = nativePeerConnection

    override suspend fun createOffer(): WebRTC.SessionDescription = withSdpException("Failed to create offer") {
        return nativePeerConnection.createOffer().await<RTCSessionDescriptionInit>().toCommon()
    }

    override suspend fun createAnswer(): WebRTC.SessionDescription = withSdpException("Failed to create answer") {
        return nativePeerConnection.createAnswer().await<RTCSessionDescriptionInit>().toCommon()
    }

    override suspend fun setLocalDescription(description: WebRTC.SessionDescription): Unit =
        withSdpException("Failed to set local description") {
            nativePeerConnection.setLocalDescription(description.toJS()).await<JsUndefined>()
        }

    override suspend fun setRemoteDescription(description: WebRTC.SessionDescription): Unit =
        withSdpException("Failed to set remote description") {
            nativePeerConnection.setRemoteDescription(description.toJS()).await<JsUndefined>()
        }

    override suspend fun addIceCandidate(candidate: WebRTC.IceCandidate): Unit =
        withIceException("Failed to add ICE candidate") {
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
    public val nativeTrack: MediaStreamTrack,
    public val nativeStream: MediaStream
) : WebRTCMedia.Track {
    public override val id: String = nativeTrack.id
    public override val kind: WebRTCMedia.TrackType = nativeTrack.kind.toTrackKind()
    public override val enabled: Boolean
        get() = nativeTrack.enabled

    override fun enable(enabled: Boolean) {
        nativeTrack.enabled = enabled
    }

    override fun getNative(): Any = nativeTrack

    override fun close() {
        nativeTrack.stop()
    }

    public companion object {
        public fun from(
            nativeTrack: MediaStreamTrack,
            nativeStream: MediaStream
        ): WasmJsMediaTrack = when (nativeTrack.kind.toTrackKind()) {
            WebRTCMedia.TrackType.AUDIO -> WasmJsAudioTrack(nativeTrack, nativeStream)
            WebRTCMedia.TrackType.VIDEO -> WasmJsVideoTrack(nativeTrack, nativeStream)
        }
    }
}

public class WasmJsAudioTrack(nativeTrack: MediaStreamTrack, nativeStream: MediaStream) :
    WebRTCMedia.AudioTrack, WasmJsMediaTrack(nativeTrack, nativeStream)

public class WasmJsVideoTrack(nativeTrack: MediaStreamTrack, nativeStream: MediaStream) :
    WebRTCMedia.VideoTrack, WasmJsMediaTrack(nativeTrack, nativeStream)

public class WasmJsRtpSender(public val nativeSender: RTCRtpSender) : WebRTC.RtpSender {
    override val dtmf: WebRTC.DtmfSender? get() = nativeSender.dtmf?.let { WasmJsDtmfSender(it) }

    override val track: WebRTCMedia.Track?
        get() = nativeSender.track?.let { WasmJsMediaTrack.from(it, MediaStream()) }

    override fun getNative(): Any = nativeSender

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

public class WasmJsDtmfSender(private val nativeSender: RTCDTMFSender) : WebRTC.DtmfSender {
    override val toneBuffer: String
        get() = nativeSender.toneBuffer.toString()
    override val canInsertDTMF: Boolean
        get() = nativeSender.canInsertDTMF.toBoolean()

    override fun getNative(): Any = nativeSender

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
