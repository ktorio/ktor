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

public class WasmJsWebRtcEngine(
    override val config: JsWebRtcEngineConfig,
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: NavigatorMediaDevices
) : WebRtcEngineBase("ktor-webrtc-wasm-js"), MediaTrackFactory by mediaTrackFactory {

    override suspend fun createPeerConnection(): WebRtcPeerConnection {
        val rtcConfig = jsObject<RTCConfiguration> {
            bundlePolicy = config.bundlePolicy.toJs().toJsString()
            rtcpMuxPolicy = config.rtcpMuxPolicy.toJs().toJsString()
            iceCandidatePoolSize = config.iceCandidatePoolSize.toJsNumber()
            iceTransportPolicy = config.iceTransportPolicy.toJs().toJsString()
            iceServers = mapIceServers(config.iceServers)
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
 * WebRtc peer connection implementation for a Wasm platform.
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
                launch { iceCandidatesFlow.emit(candidate.toCommon()) }
            }
        }

        nativePeerConnection.oniceconnectionstatechange = {
            val newState = nativePeerConnection.iceConnectionState.toString().toIceConnectionState()
            launch { iceConnectionStateFlow.emit(newState) }
        }
        nativePeerConnection.onconnectionstatechange = {
            val newState = nativePeerConnection.connectionState.toString().toConnectionState()
            launch { connectionStateFlow.emit(newState) }
        }
        nativePeerConnection.onsignalingstatechange = {
            val newState = nativePeerConnection.signalingState.toString().toSignalingState()
            launch { signalingStateFlow.emit(newState) }
        }
        nativePeerConnection.onicegatheringstatechange = {
            val newState = nativePeerConnection.iceGatheringState.toString().toIceGatheringState()
            launch { iceGatheringStateFlow.emit(newState) }
        }

        nativePeerConnection.ontrack = { event: RTCTrackEvent ->
            val stream = event.streams[0]
            stream?.onremovetrack = { e ->
                val removeEvent = TrackEvent.Remove(WasmJsMediaTrack.from(e.track, stream))
                launch { trackEventsFlow.emit(removeEvent) }
            }
            launch {
                val addEvent = TrackEvent.Add(WasmJsMediaTrack.from(event.track, stream ?: MediaStream()))
                trackEventsFlow.emit(addEvent)
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
                    statsFlow.emit(stats.toCommon())
                }
            }
        }
    }

    override fun <T> getNativeConnection(): T = nativePeerConnection as? T ?: error("T should be RTCPeerConnection")

    override val localDescription: WebRtc.SessionDescription?
        get() = nativePeerConnection.localDescription?.toCommon()

    override val remoteDescription: WebRtc.SessionDescription?
        get() = nativePeerConnection.remoteDescription?.toCommon()

    override suspend fun createOffer(): WebRtc.SessionDescription = withSdpException("Failed to create offer") {
        return nativePeerConnection.createOffer().await<RTCSessionDescription>().toCommon()
    }

    override suspend fun createAnswer(): WebRtc.SessionDescription = withSdpException("Failed to create answer") {
        return nativePeerConnection.createAnswer().await<RTCSessionDescription>().toCommon()
    }

    override suspend fun setLocalDescription(description: WebRtc.SessionDescription): Unit =
        withSdpException("Failed to set local description") {
            nativePeerConnection.setLocalDescription(description.toJS()).await<JsUndefined>()
        }

    override suspend fun setRemoteDescription(description: WebRtc.SessionDescription): Unit =
        withSdpException("Failed to set remote description") {
            nativePeerConnection.setRemoteDescription(description.toJS()).await<JsUndefined>()
        }

    override suspend fun addIceCandidate(candidate: WebRtc.IceCandidate): Unit =
        withIceException("Failed to add ICE candidate") {
            nativePeerConnection.addIceCandidate(candidate.toJS()).await<JsUndefined>()
        }

    override suspend fun addTrack(track: WebRtcMedia.Track): WebRtc.RtpSender {
        val mediaTrack = (track as WasmJsMediaTrack).nativeTrack
        val stream = MediaStream().apply { addTrack(mediaTrack) }
        return WasmJsRtpSender(nativePeerConnection.addTrack(mediaTrack, stream))
    }

    override suspend fun removeTrack(track: WebRtcMedia.Track) {
        val mediaTrack = (track as WasmJsMediaTrack).nativeTrack
        val sender = nativePeerConnection.getSenders().toArray().find { it.track == mediaTrack }
        sender?.let { nativePeerConnection.removeTrack(it) }
    }

    override suspend fun removeTrack(sender: WebRtc.RtpSender) {
        return nativePeerConnection.removeTrack((sender as WasmJsRtpSender).nativeSender)
    }

    override fun restartIce() {
        nativePeerConnection.restartIce()
    }

    override fun close() {
        nativePeerConnection.close()
    }
}
