/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import web.mediastreams.MediaStream
import web.rtc.*
import kotlin.coroutines.CoroutineContext

/**
 * WebRtc peer connection implementation for JavaScript platform.
 * @param connection The native RTCPeerConnection object.
 */
public class JsWebRtcPeerConnection(
    internal val connection: RTCPeerConnection,
    coroutineContext: CoroutineContext,
    config: WebRtcConnectionConfig
) : WebRtcPeerConnection(coroutineContext, config) {

    init {
        connection.onicecandidate = eventHandler(coroutineScope) { event ->
            event.candidate?.let { events.emitIceCandidate(it.toKtor()) }
        }

        connection.oniceconnectionstatechange = eventHandler(coroutineScope) {
            val newState = connection.iceConnectionState.toKtor()
            events.emitIceConnectionStateChange(newState)
        }
        connection.onconnectionstatechange = eventHandler(coroutineScope) {
            val newState = connection.connectionState.toKtor()
            events.emitConnectionStateChange(newState)
        }
        connection.onicegatheringstatechange = eventHandler(coroutineScope) {
            val newState = connection.iceGatheringState.toKtor()
            events.emitIceGatheringStateChange(newState)
        }
        connection.onsignalingstatechange = eventHandler(coroutineScope) {
            val newState = connection.signalingState.toKtor()
            events.emitSignalingStateChange(newState)
        }

        connection.ontrack = eventHandler(coroutineScope) { event: RTCTrackEvent ->
            val track = JsMediaTrack.from(event.track)

            // If the other peer also uses Ktor WebRTC Client, then `event.streams` should contain only this track.
            for (stream in event.streams.toArray()) {
                stream.onremovetrack = eventHandler(coroutineScope) { e ->
                    if (e.track.id == event.track.id) {
                        events.emitRemoveTrack(track)
                    }
                }
            }

            events.emitAddTrack(track)
        }

        connection.ondatachannel = eventHandler(coroutineScope) { event: RTCDataChannelEvent ->
            val channel = JsWebRtcDataChannel(event.channel, coroutineScope, DataChannelReceiveOptions())
            channel.setupEvents(events)
        }

        connection.onnegotiationneeded = eventHandler(coroutineScope) { events.emitNegotiationNeeded() }
    }

    override val localDescription: WebRtc.SessionDescription?
        get() = connection.localDescription?.toKtor()

    override val remoteDescription: WebRtc.SessionDescription?
        get() = connection.remoteDescription?.toKtor()

    override suspend fun createOffer(): WebRtc.SessionDescription = withSdpException("Failed to create offer") {
        return connection.createOffer().toKtor()
    }

    override suspend fun createAnswer(): WebRtc.SessionDescription = withSdpException("Failed to create answer") {
        return connection.createAnswer().toKtor()
    }

    override suspend fun createDataChannel(
        label: String,
        options: WebRtcDataChannelOptions.() -> Unit
    ): WebRtcDataChannel {
        val options = WebRtcDataChannelOptions().apply(options)
        val receiveOptions = DataChannelReceiveOptions().apply(options.receiveOptions)
        val nativeChannel = connection.createDataChannel(label, options.toJs())
        return JsWebRtcDataChannel(nativeChannel, coroutineScope, receiveOptions).also { it.setupEvents(events) }
    }

    override suspend fun setLocalDescription(description: WebRtc.SessionDescription): Unit =
        withSdpException("Failed to set local description") {
            connection.setLocalDescription(description.toJsLocal())
        }

    override suspend fun setRemoteDescription(description: WebRtc.SessionDescription): Unit =
        withSdpException("Failed to set remote description") {
            connection.setRemoteDescription(description.toJs())
        }

    override suspend fun addIceCandidate(candidate: WebRtc.IceCandidate): Unit =
        withIceException("Failed to add ICE candidate") {
            connection.addIceCandidate(candidate.toJs())
        }

    override suspend fun addTrack(track: WebRtcMedia.Track): WebRtc.RtpSender {
        val mediaTrack = (track as JsMediaTrack).nativeTrack
        // New MediaStream will be populated with this track and included in the `RTCPeerConnection.ontrack`
        // event received by another peer, so it can listen for track removal.
        return JsRtpSender(connection.addTrack(mediaTrack, MediaStream()))
    }

    override suspend fun removeTrack(track: WebRtcMedia.Track) {
        val mediaTrack = (track as JsMediaTrack).nativeTrack
        val sender = connection.getSenders().toArray().find { it.track?.id == mediaTrack.id }
            ?: error("Track is not found.")
        connection.removeTrack(sender)
    }

    override suspend fun removeTrack(sender: WebRtc.RtpSender) {
        val rtpSender = (sender as JsRtpSender).nativeSender
        connection.removeTrack(rtpSender)
    }

    override fun restartIce() {
        connection.restartIce()
    }

    override suspend fun getStatistics(): List<WebRtc.Stats> {
        return connection.getStats().toKtor()
    }

    override fun close() {
        super.close()
        connection.close()
    }
}

/**
 * Returns implementation of the peer connection that is used under the hood. Use it with caution.
 */
public fun WebRtcPeerConnection.getNative(): RTCPeerConnection {
    val connection = this as? JsWebRtcPeerConnection ?: error("Wrong peer connection implementation.")
    return connection.connection
}
