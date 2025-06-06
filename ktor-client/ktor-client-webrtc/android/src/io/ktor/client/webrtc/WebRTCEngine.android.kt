/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import android.content.Context
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

public class AndroidWebRtcEngineConfig : WebRtcConfig() {
    /**
     * Android application context needed to create media tracks.
     * */
    public lateinit var context: Context

    /**
     * In Android WebRtc implementation PeerConnectionFactory is coupled with the MediaTrackFactory, so if you provide a
     * custom MediaTrackFactory, you should specify PeerConnectionFactory to initialize a PeerConnection.
     * */
    public var rtcFactory: PeerConnectionFactory? = null
}

public class AndroidWebRtcEngine(
    override val config: AndroidWebRtcEngineConfig,
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: AndroidMediaDevices(config.context)
) : WebRtcEngineBase("android-webrtc"), MediaTrackFactory by mediaTrackFactory {

    private fun getLocalFactory(): PeerConnectionFactory {
        val factory = config.rtcFactory ?: (mediaTrackFactory as? AndroidMediaDevices)?.peerConnectionFactory
        if (factory == null) {
            error("Please specify custom rtcFactory for custom MediaTrackFactory")
        }
        return factory
    }

    private fun WebRtc.IceServer.toNative(): PeerConnection.IceServer {
        return PeerConnection.IceServer
            .builder(urls)
            .setUsername(username ?: "") // will throw if null
            .setPassword(credential ?: "") // will throw if null
            .createIceServer()
    }

    override suspend fun createPeerConnection(): WebRtcPeerConnection {
        val iceServers = config.iceServers.map { it.toNative() }
        val rtcConfig = RTCConfiguration(iceServers).apply {
            bundlePolicy = config.bundlePolicy.toNative()
            rtcpMuxPolicy = config.rtcpMuxPolicy.toNative()
            iceCandidatePoolSize = config.iceCandidatePoolSize
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = config.iceTransportPolicy.toNative()
        }
        return AndroidWebRtcPeerConnection(
            coroutineContext,
            config.statsRefreshRate,
            config.iceCandidatesReplay,
            config.remoteTracksReplay
        ).initialize { observer ->
            getLocalFactory().createPeerConnection(rtcConfig, observer)
        }
    }
}

public class AndroidWebRtcPeerConnection(
    override val coroutineContext: CoroutineContext,
    private val statsRefreshRate: Long,
    iceCandidatesReplay: Int,
    remoteTracksReplay: Int
) : CoroutineScope, WebRtcPeerConnection(iceCandidatesReplay, remoteTracksReplay) {
    private lateinit var peerConnection: PeerConnection

    // remember RTP senders because method PeerConnection.getSenders() disposes all returned senders
    private val rtpSenders = arrayListOf<AndroidRtpSender>()

    // helper method to break a dependency cycle (PeerConnection -> PeerConnectionFactory -> Observer)
    public fun initialize(block: (Observer) -> PeerConnection?): AndroidWebRtcPeerConnection {
        peerConnection = requireNotNull(block(createObserver()))

        // Set up statistics collection
        if (statsRefreshRate > 0) {
            launch {
                while (true) {
                    delay(statsRefreshRate)
                    val stats = suspendCoroutine { cont ->
                        peerConnection.getStats { cont.resume(it.toCommon()) }
                    }
                    statsFlow.emit(stats)
                }
            }
        }

        return this
    }

    private val hasVideo get() = rtpSenders.any { it.track?.kind == WebRtcMedia.TrackType.VIDEO }
    private val hasAudio get() = rtpSenders.any { it.track?.kind == WebRtcMedia.TrackType.AUDIO }

    private fun offerConstraints() = MediaConstraints().apply {
        if (hasAudio) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        if (hasVideo) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
    }

    private fun createObserver() = object : Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate == null) return
            launch {
                iceCandidatesFlow.emit(candidate.toCommon())
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
            if (receiver == null) return
            launch {
                receiver.track()?.let {
                    val addEvent = TrackEvent.Add(AndroidMediaTrack.from(it))
                    trackEventsFlow.emit(addEvent)
                }
            }
        }

        override fun onRemoveTrack(receiver: RtpReceiver?) {
            if (receiver == null) return
            launch {
                receiver.track()?.let {
                    val removeEvent = TrackEvent.Remove(AndroidMediaTrack.from(it))
                    trackEventsFlow.emit(removeEvent)
                }
            }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            val commonState = newState.toCommon() ?: return
            launch { iceConnectionStateFlow.emit(commonState) }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            val commonState = newState.toCommon() ?: return
            launch { connectionStateFlow.emit(commonState) }
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
            val commonState = newState.toCommon() ?: return
            launch { signalingStateFlow.emit(commonState) }
        }

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
            val commonState = newState.toCommon() ?: return
            launch { iceGatheringStateFlow.emit(commonState) }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onRenegotiationNeeded(): Unit = negotiationNeededCallback()

        // we omit streams and operate with tracks instead
        override fun onAddStream(p0: MediaStream?) = Unit
        override fun onRemoveStream(p0: MediaStream?) = Unit

        // #TODO: implement data channels
        override fun onDataChannel(dataChannel: DataChannel?) = Unit
    }

    override val localDescription: WebRtc.SessionDescription?
        get() = peerConnection.localDescription?.toCommon()

    override val remoteDescription: WebRtc.SessionDescription?
        get() = peerConnection.remoteDescription?.toCommon()

    override suspend fun createOffer(): WebRtc.SessionDescription {
        val offer = suspendCoroutine { cont ->
            peerConnection.createOffer(cont.resumeAfterSdpCreate(), offerConstraints())
        }
        return offer.toCommon()
    }

    override suspend fun createAnswer(): WebRtc.SessionDescription {
        val answer = suspendCoroutine { cont ->
            peerConnection.createAnswer(cont.resumeAfterSdpCreate(), offerConstraints())
        }
        return answer.toCommon()
    }

    override suspend fun setLocalDescription(description: WebRtc.SessionDescription) {
        suspendCoroutine { cont ->
            peerConnection.setLocalDescription(cont.resumeAfterSdpSet(), description.toNative())
        }
    }

    override suspend fun setRemoteDescription(description: WebRtc.SessionDescription) {
        suspendCoroutine { cont ->
            peerConnection.setRemoteDescription(cont.resumeAfterSdpSet(), description.toNative())
        }
    }

    override suspend fun addIceCandidate(candidate: WebRtc.IceCandidate) {
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
                    override fun onAddFailure(error: String?) = cont.resumeWithException(WebRtc.IceException(error))
                }
            )
        }
    }

    override suspend fun addTrack(track: WebRtcMedia.Track): WebRtc.RtpSender {
        val mediaTrack = track as? AndroidMediaTrack ?: error("Track should extend AndroidMediaTrack")
        val newSender = AndroidRtpSender(peerConnection.addTrack(mediaTrack.nativeTrack))
        rtpSenders.add(newSender)
        return newSender
    }

    override suspend fun removeTrack(track: WebRtcMedia.Track) {
        val mediaTrack = track as? AndroidMediaTrack ?: error("Track should extend AndroidMediaTrack")
        val sender = rtpSenders.firstOrNull { it.track?.id == mediaTrack.id } ?: return
        peerConnection.removeTrack(sender.nativeRtpSender)
    }

    override suspend fun removeTrack(sender: WebRtc.RtpSender) {
        val rtpSender = sender as? AndroidRtpSender ?: error("Sender should extend AndroidRtpSender")
        peerConnection.removeTrack(rtpSender.nativeRtpSender)
    }

    override fun restartIce() {
        peerConnection.restartIce()
    }

    override fun close() {
        peerConnection.close()
    }

    override fun <T> getNativeConnection(): T = peerConnection as? T ?: error("T should be org.webrtc.PeerConnection")
}

public object AndroidWebRtc : WebRtcClientEngineFactory<AndroidWebRtcEngineConfig> {
    override fun create(block: AndroidWebRtcEngineConfig.() -> Unit): WebRtcEngine =
        AndroidWebRtcEngine(AndroidWebRtcEngineConfig().apply(block))
}
