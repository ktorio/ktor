/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.engine

import android.content.Context
import io.ktor.client.webrtc.*
import io.ktor.client.webrtc.peer.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
import org.webrtc.RtpReceiver
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
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: AndroidMediaDevices(config.context)
) : WebRTCEngineBase("android-webrtc"), MediaTrackFactory by mediaTrackFactory {

    private fun getLocalFactory(): PeerConnectionFactory {
        val factory = config.rtcFactory ?: (mediaTrackFactory as? AndroidMediaDevices)?.peerConnectionFactory
        if (factory == null) {
            error("Please specify custom rtcFactory for custom MediaTrackFactory")
        }
        return factory
    }

    private fun WebRTC.IceServer.toNative(): PeerConnection.IceServer {
        return PeerConnection.IceServer
            .builder(urls)
            .setUsername(username)
            .setPassword(credential)
            .createIceServer()
    }

    override suspend fun createPeerConnection(): WebRtcPeerConnection {
        val iceServers = (config.iceServers + config.turnServers).map { it.toNative() }
        val rtcConfig = RTCConfiguration(iceServers).apply {
            bundlePolicy = config.bundlePolicy.toNative()
            rtcpMuxPolicy = config.rtcpMuxPolicy.toNative()
            iceCandidatePoolSize = config.iceCandidatePoolSize
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = config.iceTransportPolicy.toNative()
        }
        return AndroidWebRtcPeerConnection(coroutineContext, config.statsRefreshRate).initialize { observer ->
            getLocalFactory().createPeerConnection(rtcConfig, observer)
        }
    }
}

public class AndroidWebRtcPeerConnection(
    override val coroutineContext: CoroutineContext,
    private val statsRefreshRate: Long,
) : CoroutineScope, WebRtcPeerConnection() {
    private lateinit var peerConnection: PeerConnection

    private val rtpSenders = arrayListOf<AndroidRtpSender>()

    init {
        // Set up statistics collection
        if (statsRefreshRate > 0) {
            launch {
                while (true) {
                    delay(statsRefreshRate)
                    val stats = suspendCoroutine { cont ->
                        peerConnection.getStats { cont.resume(it.toCommon()) }
                    }
                    currentStats.emit(stats)
                }
            }
        }
    }

    public fun initialize(block: (Observer) -> PeerConnection?): AndroidWebRtcPeerConnection {
        peerConnection = requireNotNull(block(createObserver()))
        return this
    }

    private val hasVideo get() = rtpSenders.any { it.track?.kind == WebRTCMedia.TrackType.VIDEO }
    private val hasAudio get() = rtpSenders.any { it.track?.kind == WebRTCMedia.TrackType.AUDIO }

    private fun offerConstraints() = MediaConstraints().apply {
        if (hasAudio) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        if (hasVideo) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
    }

    private fun createObserver() = object : Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate == null) return
            launch {
                iceCandidates.emit(candidate.toCommon())
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
            if (receiver == null || mediaStreams == null) return
            launch {
                receiver.track()?.let { t -> remoteTracks.emit(Add(AndroidVideoTrack(t))) }
            }
        }

        override fun onRemoveTrack(receiver: RtpReceiver?) {
            if (receiver == null) return
            launch {
                receiver.track()?.let { t -> remoteTracks.emit(Remove(AndroidVideoTrack(t))) }
            }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            val commonState = newState.toCommon() ?: return
            launch { currentIceConnectionState.emit(commonState) }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            val commonState = newState.toCommon() ?: return
            launch { currentConnectionState.emit(commonState) }
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
            val commonState = newState.toCommon() ?: return
            launch { currentSignalingState.emit(commonState) }
        }

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
            val commonState = newState.toCommon() ?: return
            launch { currentIceGatheringState.emit(commonState) }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onRenegotiationNeeded(): Unit = negotiationNeededCallback()

        override fun onAddStream(p0: MediaStream?) = Unit
        override fun onRemoveStream(p0: MediaStream?) = Unit
        override fun onDataChannel(dataChannel: DataChannel?) = Unit
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
            peerConnection.addIceCandidate(
                iceCandidate,
                object : AddIceObserver {
                    override fun onAddSuccess() = cont.resume(Unit)
                    override fun onAddFailure(error: String?) = cont.resumeWithException(WebRTC.IceException(error))
                }
            )
        }
    }

    override suspend fun addTrack(track: WebRTCMedia.Track): WebRTC.RtpSender {
        val mediaTrack = track as? AndroidMediaTrack ?: error("Track should extend AndroidMediaTrack")
        val newSender = AndroidRtpSender(peerConnection.addTrack(mediaTrack.nativeTrack))
        rtpSenders.add(newSender)
        return newSender
    }

    override suspend fun removeTrack(track: WebRTCMedia.Track) {
        val mediaTrack = track as? AndroidMediaTrack ?: error("Track should extend AndroidMediaTrack")
        val sender = rtpSenders.firstOrNull { it.track?.id == mediaTrack.id } ?: return
        peerConnection.removeTrack(sender.nativeRtpSender)
    }

    override suspend fun removeTrack(sender: WebRTC.RtpSender) {
        val rtpSender = sender as? AndroidRtpSender ?: error("Sender should extend AndroidRtpSender")
        peerConnection.removeTrack(rtpSender.nativeRtpSender)
    }

    override fun restartIce() {
        peerConnection.restartIce()
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
