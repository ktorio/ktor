/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.media.JsMediaTrack
import web.events.EventHandler
import web.mediastreams.MediaStream
import web.rtc.RTCPeerConnection
import kotlin.coroutines.CoroutineContext

/**
 * WebRtc peer connection implementation for JavaScript platform.
 * @param nativePeerConnection The native RTCPeerConnection object.
 */
public class JsWebRtcPeerConnection(
    internal val nativePeerConnection: RTCPeerConnection,
    coroutineContext: CoroutineContext,
    config: WebRtcConnectionConfig,
) : WebRtcPeerConnection(coroutineContext, config) {
    init {
        nativePeerConnection.onicecandidate = EventHandler { event ->
            event.candidate?.let {
                onIceCandidate(it.toKtor())
            }
        }

        nativePeerConnection.oniceconnectionstatechange = EventHandler {
            onIceConnectionStateChange(nativePeerConnection.iceConnectionState.toKtor())
        }
        nativePeerConnection.onconnectionstatechange = EventHandler {
            onConnectionStateChange(nativePeerConnection.connectionState.toKtor())
        }
        nativePeerConnection.onicegatheringstatechange = EventHandler {
            onIceGatheringStateChange(nativePeerConnection.iceGatheringState.toKtor())
        }
        nativePeerConnection.onsignalingstatechange = EventHandler {
            onSignalingStateChange(nativePeerConnection.signalingState.toKtor())
        }

        nativePeerConnection.ontrack = EventHandler { event ->
            val track = JsMediaTrack.from(event.track)

            // If the other peer also uses Ktor WebRTC Client, then `event.streams` should contain only this track.
            for (stream in event.streams.toArray()) {
                stream.onremovetrack = EventHandler { e ->
                    if (track.id == e.track.id) {
                        onRemoveTrack(track)
                    }
                }
            }
            onAddTrack(track)
        }

        nativePeerConnection.onnegotiationneeded = EventHandler {
            negotiationNeededCallback()
        }
    }

    public override suspend fun getStatistics(): List<WebRtc.Stats> {
        return nativePeerConnection.getStatsAsync().await().toKtor()
    }

    override val localDescription: WebRtc.SessionDescription?
        get() = nativePeerConnection.localDescription?.toKtor()

    override val remoteDescription: WebRtc.SessionDescription?
        get() = nativePeerConnection.remoteDescription?.toKtor()

    override suspend fun createOffer(): WebRtc.SessionDescription = withSdpException("Failed to create offer") {
        return nativePeerConnection.createOfferAsync().await().toKtor()
    }

    override suspend fun createAnswer(): WebRtc.SessionDescription = withSdpException("Failed to create answer") {
        return nativePeerConnection.createAnswerAsync().await().toKtor()
    }

    override suspend fun setLocalDescription(description: WebRtc.SessionDescription): Unit =
        withSdpException("Failed to set local description") {
            nativePeerConnection.setLocalDescriptionAsync(description.toJsLocal()).await()
        }

    override suspend fun setRemoteDescription(description: WebRtc.SessionDescription): Unit =
        withSdpException("Failed to set remote description") {
            nativePeerConnection.setRemoteDescriptionAsync(description.toJs()).await()
        }

    override suspend fun addIceCandidate(candidate: WebRtc.IceCandidate): Unit =
        withIceException("Failed to add ICE candidate") {
            nativePeerConnection.addIceCandidateAsync(candidate.toJs()).await()
        }

    override suspend fun addTrack(track: WebRtcMedia.Track): WebRtc.RtpSender {
        val mediaTrack = (track as JsMediaTrack).nativeTrack
        // New MediaStream will be populated with this track and included in the `RTCPeerConnection.ontrack`
        // event received by another peer, so it can listen for track removal.
        return JsRtpSender(nativePeerConnection.addTrack(mediaTrack, MediaStream()))
    }

    override suspend fun removeTrack(track: WebRtcMedia.Track) {
        val mediaTrack = (track as JsMediaTrack).nativeTrack
        val sender = nativePeerConnection.getSenders().toArray().find { it.track?.id == mediaTrack.id }
        sender?.let { nativePeerConnection.removeTrack(it) }
    }

    override suspend fun removeTrack(sender: WebRtc.RtpSender) {
        val rtpSender = (sender as JsRtpSender).nativeSender
        nativePeerConnection.removeTrack(rtpSender)
    }

    override fun restartIce() {
        nativePeerConnection.restartIce()
    }

    override fun close() {
        nativePeerConnection.close()
    }
}

public fun WebRtcPeerConnection.getNative(): RTCPeerConnection {
    val conn = this as? JsWebRtcPeerConnection ?: error("Only JsWebRtcPeerConnection implementation is supported.")
    return conn.nativePeerConnection
}
