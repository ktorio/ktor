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
            val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await()
            return JsAudioTrack(mediaStream.getAudioTracks()[0], mediaStream)
        }

    override suspend fun createVideoTrack(constraints: WebRTCMedia.VideoTrackConstraints): WebRTCMedia.VideoTrack =
        withPermissionException("video") {
            val streamConstrains = MediaStreamConstraints(audio = constraints.toJS())
            val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await()
            return JsVideoTrack(mediaStream.getVideoTracks()[0], mediaStream)
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
            bundlePolicy = config.bundlePolicy.toJs()
            rtcpMuxPolicy = config.rtcpMuxPolicy.toJs()
            iceServers = buildIceServers().toTypedArray()
            iceCandidatePoolSize = config.iceCandidatePoolSize
            iceTransportPolicy = config.iceTransportPolicy.toJs()
        }
        val peerConnection = RTCPeerConnection(rtcConfig)
        return JsWebRtcPeerConnection(peerConnection, coroutineContext, config.statsRefreshRate)
    }

    private fun buildIceServers() = (config.iceServers + config.turnServers).map { iceServer ->
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
    private val statsRefreshRate: Long
) : CoroutineScope, WebRtcPeerConnection() {
    init {
        nativePeerConnection.onicecandidate = { event: RTCPeerConnectionIceEvent ->
            event.candidate?.let { candidate ->
                launch { iceCandidates.emit(candidate.toCommon()) }
            }
        }

        nativePeerConnection.oniceconnectionstatechange = {
            val newState = nativePeerConnection.iceConnectionState.toIceConnectionState()
            launch { currentIceConnectionState.emit(newState) }
        }
        nativePeerConnection.onconnectionstatechange = {
            val newState = nativePeerConnection.connectionState.toConnectionState()
            launch { currentConnectionState.emit(newState) }
        }
        nativePeerConnection.onicegatheringstatechange = {
            val newState = nativePeerConnection.iceGatheringState.toIceGatheringState()
            launch { currentIceGatheringState.emit(newState) }
        }
        nativePeerConnection.onsignalingstatechange = {
            val newState = nativePeerConnection.signalingState.toSignalingState()
            launch { currentSignalingState.emit(newState) }
        }

        nativePeerConnection.ontrack = { event: RTCTrackEvent ->
            val stream = event.streams.getOrNull(0)
            stream?.onremovetrack = { e ->
                launch { remoteTracks.emit(Remove(JsMediaTrack.from(e.track, stream!!))) }
            }
            launch {
                remoteTracks.emit(Add(JsMediaTrack.from(event.track, stream ?: MediaStream())))
            }
        }

        nativePeerConnection.onnegotiationneeded = {
            negotiationNeededCallback()
        }

        // Set up statistics collection
        if (statsRefreshRate > 0) {
            launch {
                while (true) {
                    delay(statsRefreshRate)
                    val stats = nativePeerConnection.getStats().await().toCommon()
                    currentStats.emit(stats)
                }
            }
        }
    }

    override fun getNativeConnection(): Any = nativePeerConnection

    override val localDescription: WebRTC.SessionDescription?
        get() = nativePeerConnection.localDescription?.toCommon()

    override val remoteDescription: WebRTC.SessionDescription?
        get() = nativePeerConnection.remoteDescription?.toCommon()

    override suspend fun createOffer(): WebRTC.SessionDescription = withSdpException("Failed to create offer") {
        return nativePeerConnection.createOffer().await().toCommon()
    }

    override suspend fun createAnswer(): WebRTC.SessionDescription = withSdpException("Failed to create answer") {
        return nativePeerConnection.createAnswer().await().toCommon()
    }

    override suspend fun setLocalDescription(description: WebRTC.SessionDescription): Unit =
        withSdpException("Failed to set local description") {
            nativePeerConnection.setLocalDescription(description.toJS()).await()
        }

    override suspend fun setRemoteDescription(description: WebRTC.SessionDescription): Unit =
        withSdpException("Failed to set remote description") {
            nativePeerConnection.setRemoteDescription(description.toJS()).await()
        }

    override suspend fun addIceCandidate(candidate: WebRTC.IceCandidate): Unit =
        withIceException("Failed to add ICE candidate") {
            nativePeerConnection.addIceCandidate(candidate.toJS()).await()
        }

    override suspend fun addTrack(track: WebRTCMedia.Track): WebRTC.RtpSender {
        val mediaTrack = (track as JsMediaTrack).nativeTrack
        return JsRtpSender(nativePeerConnection.addTrack(mediaTrack, track.nativeStream))
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

    override fun restartIce() {
        nativePeerConnection.restartIce()
    }
}

public abstract class JsMediaTrack(
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
        ): JsMediaTrack = when (nativeTrack.kind.toTrackKind()) {
            WebRTCMedia.TrackType.AUDIO -> JsAudioTrack(nativeTrack, nativeStream)
            WebRTCMedia.TrackType.VIDEO -> JsVideoTrack(nativeTrack, nativeStream)
        }
    }
}

public class JsAudioTrack(nativeTrack: MediaStreamTrack, nativeStream: MediaStream) :
    WebRTCMedia.AudioTrack, JsMediaTrack(nativeTrack, nativeStream)

public class JsVideoTrack(nativeTrack: MediaStreamTrack, nativeStream: MediaStream) :
    WebRTCMedia.VideoTrack, JsMediaTrack(nativeTrack, nativeStream)

public class JsRtpSender(public val nativeSender: RTCRtpSender) : WebRTC.RtpSender {
    override val dtmf: WebRTC.DtmfSender? get() = nativeSender.dtmf?.let { JsDtmfSender(it) }

    override val track: WebRTCMedia.Track? get() = nativeSender.track?.let { JsMediaTrack.from(it, MediaStream()) }

    override fun getNative(): Any = nativeSender

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

public class JsDtmfSender(private val nativeSender: RTCDTMFSender) : WebRTC.DtmfSender {
    override val toneBuffer: String
        get() = nativeSender.toneBuffer
    override val canInsertDTMF: Boolean
        get() = nativeSender.canInsertDTMF

    override fun getNative(): Any = nativeSender

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
