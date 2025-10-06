/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.media.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.*
import org.webrtc.PeerConnection.Observer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

public class AndroidWebRtcPeerConnection(
    coroutineContext: CoroutineContext,
    config: WebRtcConnectionConfig
) : WebRtcPeerConnection(coroutineContext, config) {
    private lateinit var peerConnection: PeerConnection

    // remember RTP senders because method PeerConnection.getSenders() disposes all returned senders
    private val rtpSenders = arrayListOf<AndroidRtpSender>()

    override suspend fun getStatistics(): List<WebRtc.Stats> = suspendCancellableCoroutine { cont ->
        if (this::peerConnection.isInitialized.not()) {
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        peerConnection.getStats { cont.resume(it.toKtor()) }
    }

    // helper method to break a dependency cycle (PeerConnection -> PeerConnectionFactory -> Observer)
    public fun initialize(block: (Observer) -> PeerConnection?): AndroidWebRtcPeerConnection {
        peerConnection = block(createObserver()) ?: error(
            "Failed to create peer connection. On the Android platform it is usually caused by invalid configuration. For instance, missing turn server username."
        )
        return this
    }

    private val hasVideo get() = rtpSenders.any { it.track?.kind == WebRtcMedia.TrackType.VIDEO }
    private val hasAudio get() = rtpSenders.any { it.track?.kind == WebRtcMedia.TrackType.AUDIO }

    private fun offerConstraints() = MediaConstraints().apply {
        if (hasAudio) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        if (hasVideo) mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
    }

    private inline fun runInConnectionScope(crossinline block: () -> Unit) {
        // Runs a `block` in the coroutine of the peer connection not to lose possible exceptions.
        // We are already running on the special thread, so extra dispatching is not required.
        // Moreover, dispatching the coroutine on another thread could break the internal `org.webrtc` logic.
        // For instance, it silently breaks registering a data channel observer.
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { block() }
    }

    private fun createObserver() = object : Observer {
        override fun onIceCandidate(candidate: IceCandidate?) = runInConnectionScope {
            val candidate = candidate?.toKtor() ?: return@runInConnectionScope
            events.emitIceCandidate(candidate)
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) = runInConnectionScope {
            val track = receiver?.track() ?: return@runInConnectionScope
            events.emitAddTrack(AndroidMediaTrack.from(track))
        }

        override fun onRemoveTrack(receiver: RtpReceiver?) = runInConnectionScope {
            val track = receiver?.track() ?: return@runInConnectionScope
            events.emitRemoveTrack(AndroidMediaTrack.from(track))
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) = runInConnectionScope {
            val commonState = newState.toKtor() ?: return@runInConnectionScope
            events.emitIceConnectionStateChange(commonState)
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) = runInConnectionScope {
            val commonState = newState.toKtor() ?: return@runInConnectionScope
            events.emitConnectionStateChange(commonState)
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState?) = runInConnectionScope {
            val commonState = newState.toKtor() ?: return@runInConnectionScope
            events.emitSignalingStateChange(commonState)
        }

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = runInConnectionScope {
            val commonState = newState.toKtor() ?: return@runInConnectionScope
            events.emitIceGatheringStateChange(commonState)
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onRenegotiationNeeded() = runInConnectionScope {
            events.emitNegotiationNeeded()
        }

        // we omit streams and operate with tracks instead
        override fun onAddStream(p0: MediaStream?) = Unit
        override fun onRemoveStream(p0: MediaStream?) = Unit

        override fun onDataChannel(dataChannel: DataChannel?) = runInConnectionScope {
            if (dataChannel == null) return@runInConnectionScope
            val channel = AndroidWebRtcDataChannel(
                nativeChannel = dataChannel,
                channelInit = null,
                coroutineScope = coroutineScope,
                receiveOptions = DataChannelReceiveOptions()
            )
            channel.setupEvents(events)
        }
    }

    override val localDescription: WebRtc.SessionDescription?
        get() = peerConnection.localDescription?.toKtor()

    override val remoteDescription: WebRtc.SessionDescription?
        get() = peerConnection.remoteDescription?.toKtor()

    override suspend fun createOffer(): WebRtc.SessionDescription {
        val offer = suspendCancellableCoroutine { cont ->
            peerConnection.createOffer(cont.resumeAfterSdpCreate(), offerConstraints())
        }
        return offer.toKtor()
    }

    override suspend fun createAnswer(): WebRtc.SessionDescription {
        val answer = suspendCancellableCoroutine { cont ->
            peerConnection.createAnswer(cont.resumeAfterSdpCreate(), offerConstraints())
        }
        return answer.toKtor()
    }

    override suspend fun createDataChannel(
        label: String,
        options: WebRtcDataChannelOptions.() -> Unit
    ): WebRtcDataChannel {
        val options = WebRtcDataChannelOptions().apply(options)
        val channelInit = DataChannel.Init().apply {
            if (options.id != null) {
                id = options.id!!
            }
            if (options.maxRetransmits != null) {
                maxRetransmits = options.maxRetransmits!!
            }
            if (options.maxPacketLifeTime != null) {
                maxRetransmitTimeMs = options.maxPacketLifeTime?.inWholeMilliseconds?.toInt()!!
            }
            ordered = options.ordered
            protocol = options.protocol
            negotiated = options.negotiated
        }
        val nativeChannel = peerConnection.createDataChannel(label, channelInit)
        val receiveOptions = DataChannelReceiveOptions().apply(options.receiveOptions)
        return AndroidWebRtcDataChannel(nativeChannel, channelInit, coroutineScope, receiveOptions).apply {
            setupEvents(events)
        }
    }

    override suspend fun setLocalDescription(description: WebRtc.SessionDescription) {
        suspendCancellableCoroutine { cont ->
            peerConnection.setLocalDescription(cont.resumeAfterSdpSet(), description.toNative())
        }
    }

    override suspend fun setRemoteDescription(description: WebRtc.SessionDescription) {
        suspendCancellableCoroutine { cont ->
            peerConnection.setRemoteDescription(cont.resumeAfterSdpSet(), description.toNative())
        }
    }

    override suspend fun addIceCandidate(candidate: WebRtc.IceCandidate) {
        val iceCandidate = IceCandidate(
            candidate.sdpMid,
            candidate.sdpMLineIndex,
            candidate.candidate,
        )
        suspendCancellableCoroutine { cont ->
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
        val mediaTrack = track as? AndroidMediaTrack ?: error("Track should extend AndroidMediaTrack.")
        val newSender = AndroidRtpSender(peerConnection.addTrack(mediaTrack.nativeTrack))
        rtpSenders.add(newSender)
        return newSender
    }

    override suspend fun removeTrack(track: WebRtcMedia.Track) {
        val mediaTrack = track as? AndroidMediaTrack ?: error("Track should extend AndroidMediaTrack.")
        val sender = rtpSenders.firstOrNull { it.track?.id == mediaTrack.id }
            ?: error("Track is not found.")
        if (!peerConnection.removeTrack(sender.nativeSender)) {
            error("Failed to remove track.")
        }
    }

    override suspend fun removeTrack(sender: WebRtc.RtpSender) {
        val rtpSender = sender as? AndroidRtpSender ?: error("Sender should extend AndroidRtpSender.")
        if (!peerConnection.removeTrack(rtpSender.nativeSender)) {
            error("Failed to remove track.")
        }
    }

    override fun restartIce() {
        peerConnection.restartIce()
    }

    override fun close() {
        super.close()
        peerConnection.close()
    }
}

public object AndroidWebRtc : WebRtcClientEngineFactory<AndroidWebRtcEngineConfig> {
    override fun create(block: AndroidWebRtcEngineConfig.() -> Unit): WebRtcEngine =
        AndroidWebRtcEngine(AndroidWebRtcEngineConfig().apply(block))
}
