/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import WebRTC.*
import io.ktor.client.webrtc.media.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.darwin.NSObject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS-specific implementation of a WebRTC peer connection.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.IosWebRtcConnection)
 *
 * @param coroutineContext coroutine context used to deliver connection callbacks.
 * @param config configuration describing ICE servers, media constraints, and other connection options.
 * @param createConnection factory function that creates a native peer connection instance using a provided delegate.
 */
@OptIn(ExperimentalForeignApi::class)
public class IosWebRtcConnection(
    coroutineContext: CoroutineContext,
    config: WebRtcConnectionConfig,
    createConnection: (RTCPeerConnectionDelegateProtocol) -> RTCPeerConnection?
) : WebRtcPeerConnection(coroutineContext, config) {
    internal val peerConnection: RTCPeerConnection

    init {
        peerConnection = createConnection(createDelegate()) ?: error("Failed to create peer connection.")
    }

    override suspend fun getStatistics(): List<WebRtc.Stats> = suspendCancellableCoroutine { cont ->
        peerConnection.statisticsWithCompletionHandler { stats ->
            cont.resume(stats?.toKtor() ?: emptyList())
        }
    }

    private fun createDelegate() = object : RTCPeerConnectionDelegateProtocol, NSObject() {
        override fun peerConnection(
            peerConnection: RTCPeerConnection,
            didChangeConnectionState: RTCPeerConnectionState
        ) = runInConnectionScope {
            events.emitConnectionStateChange(didChangeConnectionState.toKtor())
        }

        override fun peerConnection(
            peerConnection: RTCPeerConnection,
            didChangeIceConnectionState: RTCIceConnectionState
        ) = runInConnectionScope {
            events.emitIceConnectionStateChange(didChangeIceConnectionState.toKtor())
        }

        override fun peerConnection(
            peerConnection: RTCPeerConnection,
            didChangeIceGatheringState: RTCIceGatheringState
        ) = runInConnectionScope {
            events.emitIceGatheringStateChange(didChangeIceGatheringState.toKtor())
        }

        override fun peerConnection(
            peerConnection: RTCPeerConnection,
            didGenerateIceCandidate: RTCIceCandidate
        ) = runInConnectionScope {
            events.emitIceCandidate(didGenerateIceCandidate.toKtor())
        }

        override fun peerConnection(
            peerConnection: RTCPeerConnection,
            didChangeSignalingState: RTCSignalingState
        ) = runInConnectionScope {
            events.emitSignalingStateChange(didChangeSignalingState.toKtor())
        }

        override fun peerConnectionShouldNegotiate(peerConnection: RTCPeerConnection) = runInConnectionScope {
            events.emitNegotiationNeeded()
        }

        override fun peerConnection(
            peerConnection: RTCPeerConnection,
            didOpenDataChannel: RTCDataChannel
        ) = runInConnectionScope {
            val channel = IosWebRtcDataChannel(
                nativeChannel = didOpenDataChannel,
                coroutineScope = coroutineScope,
                receiveOptions = DataChannelReceiveOptions()
            )
            channel.setupEvents(events)
            events.emitDataChannelEvent(event = DataChannelEvent.Open(channel))
        }

        override fun peerConnection(peerConnection: RTCPeerConnection, didRemoveIceCandidates: List<*>) {}

        @ObjCSignatureOverride
        override fun peerConnection(peerConnection: RTCPeerConnection, didAddStream: RTCMediaStream) {
        }

        @ObjCSignatureOverride
        override fun peerConnection(peerConnection: RTCPeerConnection, didRemoveStream: RTCMediaStream) {
        }

        override fun peerConnection(
            peerConnection: RTCPeerConnection,
            didAddReceiver: RTCRtpReceiver,
            streams: List<*>
        ) = runInConnectionScope {
            val nativeTrack = didAddReceiver.track ?: return@runInConnectionScope
            events.emitAddTrack(track = IosMediaTrack.from(nativeTrack))
        }

        override fun peerConnection(
            peerConnection: RTCPeerConnection,
            didRemoveReceiver: RTCRtpReceiver
        ) = runInConnectionScope {
            val nativeTrack = didRemoveReceiver.track ?: return@runInConnectionScope
            events.emitRemoveTrack(track = IosMediaTrack.from(nativeTrack))
        }
    }

    override val localDescription: WebRtc.SessionDescription?
        get() = peerConnection.localDescription?.toKtor()

    override val remoteDescription: WebRtc.SessionDescription?
        get() = peerConnection.remoteDescription?.toKtor()

    private fun hasAudio() = peerConnection.senders.any { (it as RTCRtpSender).track?.kind == "audio" }
    private fun hasVideo() = peerConnection.senders.any { (it as RTCRtpSender).track?.kind == "video" }

    private fun sdpConstraints(): RTCMediaConstraints {
        return RTCMediaConstraints(
            mandatoryConstraints = mutableMapOf<Any?, String>().apply {
                if (hasAudio()) set("OfferToReceiveAudio", "true")
                if (hasVideo()) set("OfferToReceiveVideo", "true")
            },
            optionalConstraints = null
        )
    }

    override suspend fun createOffer(): WebRtc.SessionDescription {
        val offer = suspendCancellableCoroutine { cont ->
            peerConnection.offerForConstraints(
                constraints = sdpConstraints(),
                completionHandler = cont.toSdpCreateHandler()
            )
        }
        return offer.toKtor()
    }

    override suspend fun createAnswer(): WebRtc.SessionDescription {
        val answer = suspendCancellableCoroutine { cont ->
            peerConnection.answerForConstraints(
                constraints = sdpConstraints(),
                completionHandler = cont.toSdpCreateHandler()
            )
        }
        return answer.toKtor()
    }

    override suspend fun createDataChannel(
        label: String,
        options: WebRtcDataChannelOptions.() -> Unit
    ): WebRtcDataChannel {
        val options = WebRtcDataChannelOptions().apply(options)
        val configuration = RTCDataChannelConfiguration().apply {
            if (options.id != null) {
                channelId = options.id!!
            }
            if (options.maxRetransmits != null) {
                maxRetransmits = options.maxRetransmits!!
            }
            if (options.maxPacketLifeTime != null) {
                maxRetransmitTimeMs = options.maxPacketLifeTime!!.inWholeMilliseconds
            }
            isOrdered = options.ordered
            protocol = options.protocol
            isNegotiated = options.negotiated
        }
        val nativeChannel = requireNotNull(peerConnection.dataChannelForLabel(label, configuration)) {
            "Failed to create data channel with label: $label"
        }
        val receiveOptions = DataChannelReceiveOptions().apply(options.receiveOptions)
        return IosWebRtcDataChannel(nativeChannel, coroutineScope, receiveOptions).apply {
            setupEvents(events)
        }
    }

    override suspend fun setLocalDescription(description: WebRtc.SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            peerConnection.setLocalDescription(sdp = description.toIos(), completionHandler = cont.toSdpSetHandler())
        }

    override suspend fun setRemoteDescription(description: WebRtc.SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            peerConnection.setRemoteDescription(sdp = description.toIos(), completionHandler = cont.toSdpSetHandler())
        }

    override suspend fun addIceCandidate(candidate: WebRtc.IceCandidate): Unit = suspendCancellableCoroutine { cont ->
        peerConnection.addIceCandidate(candidate.toIos()) { error ->
            when {
                error == null -> cont.resume(Unit)
                else -> cont.resumeWithException(WebRtc.IceException(error.toString()))
            }
        }
    }

    override suspend fun addTrack(track: WebRtcMedia.Track): WebRtc.RtpSender {
        val mediaTrack = (track as IosMediaTrack).nativeTrack
        val nativeSender = peerConnection.addTrack(mediaTrack, streamIds = listOf<String>())
            ?: error("Failed to add track.")
        return IosRtpSender(nativeSender)
    }

    override suspend fun removeTrack(track: WebRtcMedia.Track) {
        val track = track as IosMediaTrack
        val sender = peerConnection.senders.firstOrNull { (it as RTCRtpSender).track?.trackId == track.id }
            ?: error("Failed to find sender for the track.")
        peerConnection.removeTrack(sender as RTCRtpSender)
    }

    override suspend fun removeTrack(sender: WebRtc.RtpSender) {
        val rtpSender = sender as IosRtpSender
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

/**
 * Returns implementation of the peer connection that is used under the hood. Use it with caution.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.getNative)
 */
@OptIn(ExperimentalForeignApi::class)
public fun WebRtcPeerConnection.getNative(): RTCPeerConnection {
    return (this as IosWebRtcConnection).peerConnection
}
