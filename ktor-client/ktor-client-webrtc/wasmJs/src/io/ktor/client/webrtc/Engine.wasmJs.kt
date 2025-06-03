/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.browser.*
import io.ktor.client.webrtc.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.mediacapture.MediaStream
import kotlin.coroutines.CoroutineContext

public class WasmJsWebRTCEngine(
    override val config: JsWebRTCEngineConfig,
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: NavigatorMediaDevices
) : WebRTCEngineBase("ktor-webrtc-wasm-js"), MediaTrackFactory by mediaTrackFactory {

    override suspend fun createPeerConnection(): WebRtcPeerConnection {
        val rtcConfig = jsObject<RTCConfiguration> {
            bundlePolicy = config.bundlePolicy.toJs().toJsString()
            rtcpMuxPolicy = config.rtcpMuxPolicy.toJs().toJsString()
            iceCandidatePoolSize = config.iceCandidatePoolSize.toJsNumber()
            iceTransportPolicy = config.iceTransportPolicy.toJs().toJsString()
            iceServers = mapIceServers(config.iceServers + config.turnServers)
        }
        val peerConnection = RTCPeerConnection(rtcConfig)
        return WasmJsWebRtcPeerConnection(
            peerConnection,
            coroutineContext,
            config.statsRefreshRate,
            config.iceCandidatesReplay,
            config.remoteTracksReplay
        )
    }
}

/**
 * WebRTC peer connection implementation for a Wasm platform.
 * @param nativePeerConnection The native RTCPeerConnection object.
 */
public class WasmJsWebRtcPeerConnection(
    private val nativePeerConnection: RTCPeerConnection,
    override val coroutineContext: CoroutineContext,
    private val statsRefreshRate: Long,
    iceCandidatesReplay: Int,
    remoteTracksReplay: Int
) : CoroutineScope, WebRtcPeerConnection(iceCandidatesReplay, remoteTracksReplay) {
    init {
        nativePeerConnection.onicecandidate = { event: RTCPeerConnectionIceEvent ->
            event.candidate?.let { candidate ->
                launch { iceCandidates.emit(candidate.toCommon()) }
            }
        }

        nativePeerConnection.oniceconnectionstatechange = {
            val newState = nativePeerConnection.iceConnectionState.toString().toIceConnectionState()
            launch { currentIceConnectionState.emit(newState) }
        }
        nativePeerConnection.onconnectionstatechange = {
            val newState = nativePeerConnection.connectionState.toString().toConnectionState()
            launch { currentConnectionState.emit(newState) }
        }
        nativePeerConnection.onsignalingstatechange = {
            val newState = nativePeerConnection.signalingState.toString().toSignalingState()
            launch { currentSignalingState.emit(newState) }
        }
        nativePeerConnection.onicegatheringstatechange = {
            val newState = nativePeerConnection.iceGatheringState.toString().toIceGatheringState()
            launch { currentIceGatheringState.emit(newState) }
        }

        nativePeerConnection.ontrack = { event: RTCTrackEvent ->
            val stream = event.streams[0]
            stream?.onremovetrack = { e ->
                launch { remoteTracks.emit(Remove(WasmJsMediaTrack.from(e.track, stream))) }
            }
            launch {
                remoteTracks.emit(Add(WasmJsMediaTrack.from(event.track, stream ?: MediaStream())))
            }
        }

        nativePeerConnection.onnegotiationneeded = {
            negotiationNeededCallback()
        }

        // Set up statistics collection
        if (statsRefreshRate > 0) {
            launch {
                while (true) {
                    delay(statsRefreshRate)
                    val stats = nativePeerConnection.getStats().await<RTCStatsReport>()
                    currentStats.emit(stats.toCommon())
                }
            }
        }
    }

    override fun getNativeConnection(): Any = nativePeerConnection

    override val localDescription: WebRTC.SessionDescription?
        get() = nativePeerConnection.localDescription?.toCommon()

    override val remoteDescription: WebRTC.SessionDescription?
        get() = nativePeerConnection.remoteDescription?.toCommon()

    override suspend fun createOffer(): WebRTC.SessionDescription = withSdpException("Failed to create offer") {
        return nativePeerConnection.createOffer().await<RTCSessionDescription>().toCommon()
    }

    override suspend fun createAnswer(): WebRTC.SessionDescription = withSdpException("Failed to create answer") {
        return nativePeerConnection.createAnswer().await<RTCSessionDescription>().toCommon()
    }

    override suspend fun setLocalDescription(description: WebRTC.SessionDescription): Unit =
        withSdpException("Failed to set local description") {
            nativePeerConnection.setLocalDescription(description.toJS()).await<JsUndefined>()
        }

    override suspend fun setRemoteDescription(description: WebRTC.SessionDescription): Unit =
        withSdpException("Failed to set remote description") {
            nativePeerConnection.setRemoteDescription(description.toJS()).await<JsUndefined>()
        }

    override suspend fun addIceCandidate(candidate: WebRTC.IceCandidate): Unit =
        withIceException("Failed to add ICE candidate") {
            nativePeerConnection.addIceCandidate(candidate.toJS()).await<JsUndefined>()
        }

    override suspend fun addTrack(track: WebRTCMedia.Track): WebRTC.RtpSender {
        val mediaTrack = (track as WasmJsMediaTrack).nativeTrack
        val stream = MediaStream().apply { addTrack(mediaTrack) }
        return WasmJsRtpSender(nativePeerConnection.addTrack(mediaTrack, stream))
    }

    override suspend fun removeTrack(track: WebRTCMedia.Track) {
        val mediaTrack = (track as WasmJsMediaTrack).nativeTrack
        val sender = nativePeerConnection.getSenders().toArray().find { it.track == mediaTrack }
        sender?.let { nativePeerConnection.removeTrack(it) }
    }

    override suspend fun removeTrack(sender: WebRTC.RtpSender) {
        return nativePeerConnection.removeTrack((sender as WasmJsRtpSender).nativeSender)
    }

    override fun restartIce() {
        nativePeerConnection.restartIce()
    }

    override fun close() {
        nativePeerConnection.close()
    }
}
