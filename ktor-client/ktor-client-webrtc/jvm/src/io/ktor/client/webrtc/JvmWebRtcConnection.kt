/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import dev.onvoid.webrtc.PeerConnectionObserver
import dev.onvoid.webrtc.RTCAnswerOptions
import dev.onvoid.webrtc.RTCDataChannel
import dev.onvoid.webrtc.RTCDataChannelInit
import dev.onvoid.webrtc.RTCIceCandidate
import dev.onvoid.webrtc.RTCIceConnectionState
import dev.onvoid.webrtc.RTCIceGatheringState
import dev.onvoid.webrtc.RTCOfferOptions
import dev.onvoid.webrtc.RTCPeerConnection
import dev.onvoid.webrtc.RTCPeerConnectionState
import dev.onvoid.webrtc.RTCRtpReceiver
import dev.onvoid.webrtc.RTCSignalingState
import dev.onvoid.webrtc.media.MediaStream
import io.ktor.client.webrtc.media.JvmMediaTrack
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

/**
 * JVM-specific implementation of a WebRTC peer connection based on `dev.onvoid.webrtc`.
 */
public class JvmWebRtcConnection(
    coroutineContext: CoroutineContext,
    config: WebRtcConnectionConfig
) : WebRtcPeerConnection(coroutineContext, config) {

    private lateinit var inner: RTCPeerConnection

    /**
     * Installs a native PeerConnection instance created by the engine.
     */
    public fun initialize(block: (PeerConnectionObserver) -> RTCPeerConnection): JvmWebRtcConnection {
        if (this::inner.isInitialized) {
            error("Peer connection has been already initialized.")
        }
        inner = block(createObserver())
        return this
    }

    override val localDescription: WebRtc.SessionDescription?
        get() = inner.localDescription?.toKtor()

    override val remoteDescription: WebRtc.SessionDescription?
        get() = inner.remoteDescription?.toKtor()

    private inline fun runInConnectionScope(crossinline block: () -> Unit) {
        // Runs a `block` in the coroutine of the peer connection not to lose possible exceptions.
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { block() }
    }

    private fun createObserver() = object : PeerConnectionObserver {
        override fun onConnectionChange(state: RTCPeerConnectionState?) = runInConnectionScope {
            if (state == null) return@runInConnectionScope
            events.emitConnectionStateChange(state.toKtor())
        }

        override fun onIceConnectionChange(state: RTCIceConnectionState?) = runInConnectionScope {
            if (state == null) return@runInConnectionScope
            events.emitIceConnectionStateChange(state.toKtor())
        }

        override fun onIceGatheringChange(state: RTCIceGatheringState?) = runInConnectionScope {
            if (state == null) return@runInConnectionScope
            events.emitIceGatheringStateChange(state.toKtor())
        }

        override fun onIceCandidate(candidate: RTCIceCandidate?) = runInConnectionScope {
            if (candidate == null) return@runInConnectionScope
            events.emitIceCandidate(candidate.toKtor())
        }

        override fun onSignalingChange(state: RTCSignalingState?) = runInConnectionScope {
            if (state == null) return@runInConnectionScope
            events.emitSignalingStateChange(state.toKtor())
        }

        override fun onRenegotiationNeeded() = runInConnectionScope {
            events.emitNegotiationNeeded()
        }

        override fun onDataChannel(dataChannel: RTCDataChannel?) = runInConnectionScope {
            if (dataChannel == null) return@runInConnectionScope
            val channel = JvmWebRtcDataChannel(
                inner = dataChannel,
                coroutineScope = coroutineScope,
                receiveOptions = DataChannelReceiveOptions()
            )
            channel.setupEvents(events)
            events.emitDataChannelEvent(event = DataChannelEvent.Open(channel))
        }

        override fun onAddTrack(receiver: RTCRtpReceiver?, mediaStreams: Array<out MediaStream?>?) =
            runInConnectionScope {
                val nativeTrack = receiver?.track ?: return@runInConnectionScope
                events.emitAddTrack(track = JvmMediaTrack.from(nativeTrack))
            }

        override fun onRemoveTrack(receiver: RTCRtpReceiver?) = runInConnectionScope {
            val nativeTrack = receiver?.track ?: return@runInConnectionScope
            events.emitRemoveTrack(track = JvmMediaTrack.from(nativeTrack))
        }
    }

    override suspend fun createOffer(): WebRtc.SessionDescription {
        return suspendCancellableCoroutine { cont ->
            val options = RTCOfferOptions()
            inner.createOffer(options, cont.resumeAfterSdpCreate())
        }
    }

    override suspend fun createAnswer(): WebRtc.SessionDescription {
        return suspendCancellableCoroutine { cont ->
            val options = RTCAnswerOptions()
            inner.createAnswer(options, cont.resumeAfterSdpCreate())
        }
    }

    override suspend fun createDataChannel(
        label: String,
        options: WebRtcDataChannelOptions.() -> Unit
    ): WebRtcDataChannel {
        val options = WebRtcDataChannelOptions().apply(options)

        // check it here because it could break `dev.onvoid.webrtc` native library
        require(options.maxRetransmits == null || options.maxPacketLifeTime == null) {
            "Both maxRetransmits and maxPacketLifeTime can't be set at the same time."
        }

        val channelInit = RTCDataChannelInit().apply {
            if (options.id != null) {
                id = options.id!!
            }
            if (options.maxRetransmits != null) {
                maxRetransmits = options.maxRetransmits!!
            }
            if (options.maxPacketLifeTime != null) {
                maxPacketLifeTime = options.maxPacketLifeTime?.inWholeMilliseconds?.toInt()!!
            }
            ordered = options.ordered
            protocol = options.protocol
            negotiated = options.negotiated
        }
        val nativeChannel = inner.createDataChannel(label, channelInit)
        val receiveOptions = DataChannelReceiveOptions().apply(options.receiveOptions)
        return JvmWebRtcDataChannel(nativeChannel, coroutineScope, receiveOptions).apply {
            setupEvents(events)
        }
    }

    private inline fun withSdpException(message: String, block: () -> Unit): Unit = try {
        block()
    } catch (cause: Throwable) {
        throw WebRtc.SdpException(message, cause)
    }

    override suspend fun setLocalDescription(description: WebRtc.SessionDescription) {
        suspendCancellableCoroutine { cont ->
            withSdpException("Failed to set local description") {
                inner.setLocalDescription(description.toJvm(), cont.resumeAfterSdpSet())
            }
        }
    }

    override suspend fun setRemoteDescription(description: WebRtc.SessionDescription) {
        suspendCancellableCoroutine { cont ->
            withSdpException("Failed to set remote description") {
                inner.setRemoteDescription(description.toJvm(), cont.resumeAfterSdpSet())
            }
        }
    }

    override suspend fun addIceCandidate(candidate: WebRtc.IceCandidate) {
        try {
            inner.addIceCandidate(candidate.toJvm())
        } catch (cause: Throwable) {
            throw WebRtc.IceException("Failed to add ICE candidate", cause)
        }
    }

    override suspend fun addTrack(track: WebRtcMedia.Track): WebRtc.RtpSender {
        val nativeTrack = (track as JvmMediaTrack).inner
        val nativeSender = inner.addTrack(nativeTrack, listOf<String>())
        return JvmRtpSender(inner = nativeSender)
    }

    override suspend fun removeTrack(sender: WebRtc.RtpSender) {
        inner.removeTrack((sender as JvmRtpSender).inner)
    }

    override suspend fun removeTrack(track: WebRtcMedia.Track) {
        val mediaTrack = track as JvmMediaTrack
        val sender = inner.senders.firstOrNull { it.track.id == mediaTrack.id }
            ?: error("Track is not found.")
        inner.removeTrack(sender)
    }

    override fun restartIce() {
        inner.restartIce()
    }

    override suspend fun getStatistics(): List<WebRtc.Stats> {
        return suspendCancellableCoroutine { cont ->
            if (!this::inner.isInitialized) {
                cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            inner.getStats { statsReport ->
                cont.resume(statsReport.toKtor())
            }
        }
    }

    override fun close() {
        super.close()
        inner.close()
    }
}
