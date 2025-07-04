/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.media.AndroidMediaTrack
import kotlinx.coroutines.launch
import org.webrtc.AddIceObserver
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.Observer
import org.webrtc.RtpReceiver
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

public class AndroidWebRtcPeerConnection(
    coroutineContext: CoroutineContext,
    config: WebRtcConnectionConfig
) : WebRtcPeerConnection(coroutineContext, config) {
    private lateinit var peerConnection: PeerConnection

    // remember RTP senders because method PeerConnection.getSenders() disposes all returned senders
    private val rtpSenders = arrayListOf<AndroidRtpSender>()

    override suspend fun getStatistics(): List<WebRtc.Stats> = suspendCoroutine { cont ->
        if (this::peerConnection.isInitialized.not()) {
            cont.resume(emptyList())
        }
        peerConnection.getStats { cont.resume(it.toCommon()) }
    }

    // helper method to break a dependency cycle (PeerConnection -> PeerConnectionFactory -> Observer)
    public fun initialize(block: (Observer) -> PeerConnection?): AndroidWebRtcPeerConnection {
        return this.also { peerConnection = requireNotNull(block(createObserver())) }
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
            events.emitIceCandidate(candidate.toCommon())
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
            if (receiver == null) return
            launch {
                receiver.track()?.let {
                    events.emitAddTrack(AndroidMediaTrack.from(it))
                }
            }
        }

        override fun onRemoveTrack(receiver: RtpReceiver?) {
            if (receiver == null) return
            launch {
                receiver.track()?.let {
                    events.emitRemoveTrack(AndroidMediaTrack.from(it))
                }
            }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            val commonState = newState.toCommon() ?: return
            events.emitIceConnectionStateChange(commonState)
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            val commonState = newState.toCommon() ?: return
            events.emitConnectionStateChange(commonState)
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
            val commonState = newState.toCommon() ?: return
            events.emitSignalingStateChange(commonState)
        }

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
            val commonState = newState.toCommon() ?: return
            events.emitIceGatheringStateChange(commonState)
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onRenegotiationNeeded(): Unit = events.emitNegotiationNeeded()

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
        peerConnection.removeTrack(sender.nativeSender)
    }

    override suspend fun removeTrack(sender: WebRtc.RtpSender) {
        val rtpSender = sender as? AndroidRtpSender ?: error("Sender should extend AndroidRtpSender")
        peerConnection.removeTrack(rtpSender.nativeSender)
    }

    override fun restartIce() {
        peerConnection.restartIce()
    }

    override fun close() {
        peerConnection.close()
    }
}

public object AndroidWebRtc : WebRtcClientEngineFactory<AndroidWebRtcEngineConfig> {
    override fun create(block: AndroidWebRtcEngineConfig.() -> Unit): WebRtcEngine =
        AndroidWebRtcEngine(AndroidWebRtcEngineConfig().apply(block))
}
