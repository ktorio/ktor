/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client.engine

import android.content.Context
import io.ktor.webrtc.client.*
import io.ktor.webrtc.client.peer.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.AddIceObserver
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.Observer
import org.webrtc.PeerConnection.RTCConfiguration
import org.webrtc.PeerConnectionFactory
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

public class AndroidWebRTCEngineConfig : WebRTCConfig() {
    public lateinit var context: Context
    public var rtcFactory: PeerConnectionFactory? = null
}

public class AndroidWebRTCEngine(
    override val config: AndroidWebRTCEngineConfig,
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: DefaultMediaDevices(config.context)
) : WebRTCEngineBase("android-webrtc"), MediaTrackFactory by mediaTrackFactory {

    private fun getLocalFactory(): PeerConnectionFactory {
        val factory = config.rtcFactory ?: (mediaTrackFactory as? DefaultMediaDevices)?.peerConnectionFactory
        if (factory == null) {
            error("Please specify custom rtcFactory for custom MediaTrackFactory")
        }
        return factory
    }

    override suspend fun createPeerConnection(): WebRtcPeerConnection {
        val rtcConfig = RTCConfiguration(
            config.iceServers.map {
                PeerConnection.IceServer.builder(it.urls).createIceServer()
            }
        ).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }

        return AndroidWebRtcPeerConnection(coroutineContext, config.statsRefreshRate).initialize { observer ->
            getLocalFactory().createPeerConnection(rtcConfig, observer)
        }
    }
}

public class AndroidWebRtcPeerConnection(
    override val coroutineContext: CoroutineContext,
    private val statsRefreshRate: Long,
) : CoroutineScope, WebRtcPeerConnection {
    private lateinit var peerConnection: PeerConnection

    private val _iceCandidateFlow = MutableSharedFlow<WebRTC.IceCandidate>()
    override val iceCandidateFlow: SharedFlow<WebRTC.IceCandidate> = _iceCandidateFlow.asSharedFlow()

    private val _statsFlow = MutableStateFlow(listOf<WebRTC.Stats>())
    override val statsFlow: StateFlow<List<WebRTC.Stats>> = _statsFlow.asStateFlow()

    init {
        // Set up statistics collection
        if (statsRefreshRate > 0) launch {
            while (true) {
                delay(statsRefreshRate)
                val stats = suspendCoroutine { cont ->
                    peerConnection.getStats { cont.resume(it.toCommon()) }
                }
                _statsFlow.emit(stats)
            }
        }
    }

    public fun initialize(block: (Observer) -> PeerConnection?): AndroidWebRtcPeerConnection {
        peerConnection = requireNotNull(block(createObserver()))
        return this
    }

    private val hasVideo get() = peerConnection.senders.any { it.track()?.kind() == "video" }
    private val hasAudio get() = peerConnection.senders.any { it.track()?.kind() == "audio" }

    private fun offerConstraints() = MediaConstraints().apply {
        if (hasAudio) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        if (hasVideo) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
    }

    private fun createObserver() = object : Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate == null) {
                return
            }
            launch {
                _iceCandidateFlow.emit(
                    WebRTC.IceCandidate(
                        candidate = candidate.sdp,
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex
                    )
                )
            }
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(dataChannel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    override suspend fun createOffer(): WebRTC.SessionDescription {
        val offer = suspendCoroutine { cont ->
            peerConnection.createOffer(cont.resumeAfterSdpCreate(), offerConstraints())
        }
        return offer.toCommon()
    }

    override suspend fun createAnswer(): WebRTC.SessionDescription {
        val answer = suspendCoroutine { cont ->
            peerConnection.createAnswer(cont.resumeAfterSdpCreate(), offerConstraints())
        }
        return answer.toCommon()
    }

    override suspend fun setLocalDescription(description: WebRTC.SessionDescription) {
        suspendCoroutine { cont ->
            peerConnection.setLocalDescription(cont.resumeAfterSdpSet(), description.toNative())
        }
    }

    override suspend fun setRemoteDescription(description: WebRTC.SessionDescription) {
        suspendCoroutine { cont ->
            peerConnection.setRemoteDescription(cont.resumeAfterSdpSet(), description.toNative())
        }
    }

    override suspend fun addIceCandidate(candidate: WebRTC.IceCandidate) {
        val iceCandidate = IceCandidate(
            candidate.sdpMid,
            candidate.sdpMLineIndex,
            candidate.candidate,
        )
        suspendCoroutine { cont ->
            peerConnection.addIceCandidate(iceCandidate, object : AddIceObserver {
                override fun onAddSuccess() = cont.resume(Unit)
                override fun onAddFailure(error: String?) = cont.resumeWithException(Throwable(error))
            })
        }
    }

    override suspend fun addTrack(track: WebRTCMedia.Track): WebRTC.RtpSender {
        if (track is AndroidMediaTrack) {
            return AndroidRtpSender(peerConnection.addTrack(track.nativeTrack))
        } else {
            throw Throwable("Track should extend AndroidMediaTrack")
        }
    }

    override suspend fun removeTrack(track: WebRTCMedia.Track) {
        val sender = peerConnection.senders.firstOrNull { it.track() == track }
        peerConnection.removeTrack(sender)
    }

    override suspend fun removeTrack(sender: WebRTC.RtpSender) {
        peerConnection.removeTrack((sender as? AndroidRtpSender)?.nativeRtpSender)
    }

    override fun close() {
        peerConnection.close()
    }

    override fun getNativeConnection(): Any = peerConnection
}

public object AndroidWebRTC : WebRTCClientEngineFactory<AndroidWebRTCEngineConfig> {
    override fun create(block: AndroidWebRTCEngineConfig.() -> Unit): WebRTCEngine =
        AndroidWebRTCEngine(AndroidWebRTCEngineConfig().apply(block))
}
