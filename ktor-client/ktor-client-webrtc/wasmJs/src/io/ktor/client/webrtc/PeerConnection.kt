/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.browser.*
import io.ktor.client.webrtc.utils.*
import kotlinx.coroutines.await
import org.w3c.dom.mediacapture.MediaStream
import kotlin.coroutines.CoroutineContext

/**
 * WebRtc peer connection implementation for a Wasm platform.
 * @param nativePeerConnection The native RTCPeerConnection object.
 */
public class WasmJsWebRtcPeerConnection(
    internal val nativePeerConnection: RTCPeerConnection,
    coroutineContext: CoroutineContext,
    config: WebRtcConnectionConfig
) : WebRtcPeerConnection(coroutineContext, config) {
    init {
        nativePeerConnection.onicecandidate = { event: RTCPeerConnectionIceEvent ->
            event.candidate?.let { events.emitIceCandidate(it.toKtor()) }
        }

        nativePeerConnection.oniceconnectionstatechange = {
            val newState = nativePeerConnection.iceConnectionState.toString().toIceConnectionState()
            events.emitIceConnectionStateChange(newState)
        }
        nativePeerConnection.onconnectionstatechange = {
            val newState = nativePeerConnection.connectionState.toString().toConnectionState()
            events.emitConnectionStateChange(newState)
        }
        nativePeerConnection.onicegatheringstatechange = {
            val newState = nativePeerConnection.iceGatheringState.toString().toIceGatheringState()
            events.emitIceGatheringStateChange(newState)
        }
        nativePeerConnection.onsignalingstatechange = {
            val newState = nativePeerConnection.signalingState.toString().toSignalingState()
            events.emitSignalingStateChange(newState)
        }

        nativePeerConnection.ontrack = { event: RTCTrackEvent ->
            val track = WasmJsMediaTrack.from(event.track)
            for (stream in event.streams.toArray()) {
                stream.onremovetrack = { e ->
                    if (e.track.id == event.track.id) {
                        events.emitRemoveTrack(track)
                    }
                }
            }
            events.emitAddTrack(track)
        }

        nativePeerConnection.ondatachannel = { event: RTCDataChannelEvent ->
            val channel = WasmJsWebRtcDataChannel(event.channel, DataChannelReceiveOptions())
            channel.setupEvents(events)
        }

        nativePeerConnection.onnegotiationneeded = { events.emitNegotiationNeeded() }
    }

    override val localDescription: WebRtc.SessionDescription?
        get() = nativePeerConnection.localDescription?.toKtor()

    override val remoteDescription: WebRtc.SessionDescription?
        get() = nativePeerConnection.remoteDescription?.toKtor()

    override suspend fun createOffer(): WebRtc.SessionDescription = withSdpException("Failed to create offer") {
        return nativePeerConnection.createOffer().await<RTCSessionDescription>().toKtor()
    }

    override suspend fun createAnswer(): WebRtc.SessionDescription = withSdpException("Failed to create answer") {
        return nativePeerConnection.createAnswer().await<RTCSessionDescription>().toKtor()
    }

    override suspend fun createDataChannel(
        label: String,
        options: WebRtcDataChannelOptions.() -> Unit
    ): WebRtcDataChannel {
        val options = WebRtcDataChannelOptions().apply(options)
        val receiveOptions = DataChannelReceiveOptions().apply(options.receiveOptions)
        val nativeChannel = nativePeerConnection.createDataChannel(label.toJsString(), options.toJs())
        return WasmJsWebRtcDataChannel(nativeChannel, receiveOptions).also { it.setupEvents(events) }
    }

    override suspend fun setLocalDescription(description: WebRtc.SessionDescription): Unit =
        withSdpException("Failed to set local description") {
            nativePeerConnection.setLocalDescription(description.toJs()).await()
        }

    override suspend fun setRemoteDescription(description: WebRtc.SessionDescription): Unit =
        withSdpException("Failed to set remote description") {
            nativePeerConnection.setRemoteDescription(description.toJs()).await()
        }

    override suspend fun addIceCandidate(candidate: WebRtc.IceCandidate): Unit =
        withIceException("Failed to add ICE candidate") {
            nativePeerConnection.addIceCandidate(candidate.toJs()).await()
        }

    override suspend fun addTrack(track: WebRtcMedia.Track): WebRtc.RtpSender {
        val mediaTrack = (track as WasmJsMediaTrack).nativeTrack
        return WasmJsRtpSender(nativePeerConnection.addTrack(mediaTrack, MediaStream()))
    }

    override suspend fun removeTrack(track: WebRtcMedia.Track) {
        val mediaTrack = (track as WasmJsMediaTrack).nativeTrack
        val sender = nativePeerConnection.getSenders().toArray().find { it.track == mediaTrack }
        sender?.let { nativePeerConnection.removeTrack(it) }
    }

    override suspend fun removeTrack(sender: WebRtc.RtpSender) {
        val rtpSender = (sender as WasmJsRtpSender).nativeSender
        nativePeerConnection.removeTrack(rtpSender)
    }

    override fun restartIce() {
        nativePeerConnection.restartIce()
    }

    override suspend fun getStatistics(): List<WebRtc.Stats> {
        return nativePeerConnection.getStats().await<RTCStatsReport>().toKtor()
    }

    override fun close() {
        nativePeerConnection.close()
    }
}

public fun WebRtcPeerConnection.getNative(): RTCPeerConnection {
    val connection = this as? WasmJsWebRtcPeerConnection ?: error("Wrong peer connection implementation.")
    return connection.nativePeerConnection
}
