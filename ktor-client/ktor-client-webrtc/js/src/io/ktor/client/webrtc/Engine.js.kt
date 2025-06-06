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

public class JsWebRtcEngine(
    override val config: JsWebRtcEngineConfig,
    private val mediaTrackFactory: MediaTrackFactory = config.mediaTrackFactory ?: NavigatorMediaDevices
) : WebRtcEngineBase("js-webrtc"), MediaTrackFactory by mediaTrackFactory {

    override suspend fun createPeerConnection(): WebRtcPeerConnection {
        val rtcConfig = jsObject<RTCConfiguration> {
            bundlePolicy = config.bundlePolicy.toJs()
            rtcpMuxPolicy = config.rtcpMuxPolicy.toJs()
            iceServers = buildIceServers().toTypedArray()
            iceCandidatePoolSize = config.iceCandidatePoolSize
            iceTransportPolicy = config.iceTransportPolicy.toJs()
        }
        val peerConnection = RTCPeerConnection(rtcConfig)
        return JsWebRtcPeerConnection(
            peerConnection,
            coroutineContext,
            config.statsRefreshRate,
            config.iceCandidatesReplay,
            config.remoteTracksReplay
        )
    }

    private fun buildIceServers() = config.iceServers.map { iceServer ->
        jsObject<RTCIceServer> {
            urls = iceServer.urls
            username = iceServer.username
            credential = iceServer.credential
        }
    }
}

/**
 * WebRtc peer connection implementation for JavaScript platform.
 * @param nativePeerConnection The native RTCPeerConnection object.
 */
public class JsWebRtcPeerConnection(
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
            val newState = nativePeerConnection.iceConnectionState.toIceConnectionState()
            launch { iceConnectionStateFlow.emit(newState) }
        }
        nativePeerConnection.onconnectionstatechange = {
            val newState = nativePeerConnection.connectionState.toConnectionState()
            launch { connectionStateFlow.emit(newState) }
        }
        nativePeerConnection.onicegatheringstatechange = {
            val newState = nativePeerConnection.iceGatheringState.toIceGatheringState()
            launch { iceGatheringStateFlow.emit(newState) }
        }
        nativePeerConnection.onsignalingstatechange = {
            val newState = nativePeerConnection.signalingState.toSignalingState()
            launch { signalingStateFlow.emit(newState) }
        }

        nativePeerConnection.ontrack = { event: RTCTrackEvent ->
            val stream = event.streams.getOrNull(0)
            stream?.onremovetrack = { e ->
                val addEvent = TrackEvent.Remove(JsMediaTrack.from(e.track, stream))
                launch { trackEventsFlow.emit(addEvent) }
            }
            launch {
                val removeEvent = TrackEvent.Add(JsMediaTrack.from(event.track, stream ?: MediaStream()))
                trackEventsFlow.emit(removeEvent)
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
                    val stats = nativePeerConnection.getStats().await().toCommon()
                    statsFlow.emit(stats)
                }
            }
        }
    }

    override fun <T> getNativeConnection(): T = nativePeerConnection as? T ?: error("Invalid connection type")

    override val localDescription: WebRtc.SessionDescription?
        get() = nativePeerConnection.localDescription?.toCommon()

    override val remoteDescription: WebRtc.SessionDescription?
        get() = nativePeerConnection.remoteDescription?.toCommon()

    override suspend fun createOffer(): WebRtc.SessionDescription = withSdpException("Failed to create offer") {
        return nativePeerConnection.createOffer().await().toCommon()
    }

    override suspend fun createAnswer(): WebRtc.SessionDescription = withSdpException("Failed to create answer") {
        return nativePeerConnection.createAnswer().await().toCommon()
    }

    override suspend fun setLocalDescription(description: WebRtc.SessionDescription): Unit =
        withSdpException("Failed to set local description") {
            nativePeerConnection.setLocalDescription(description.toJS()).await()
        }

    override suspend fun setRemoteDescription(description: WebRtc.SessionDescription): Unit =
        withSdpException("Failed to set remote description") {
            nativePeerConnection.setRemoteDescription(description.toJS()).await()
        }

    override suspend fun addIceCandidate(candidate: WebRtc.IceCandidate): Unit =
        withIceException("Failed to add ICE candidate") {
            nativePeerConnection.addIceCandidate(candidate.toJS()).await()
        }

    override suspend fun addTrack(track: WebRtcMedia.Track): WebRtc.RtpSender {
        val mediaTrack = (track as JsMediaTrack).nativeTrack
        return JsRtpSender(nativePeerConnection.addTrack(mediaTrack, track.nativeStream))
    }

    override suspend fun removeTrack(track: WebRtcMedia.Track) {
        val mediaTrack = (track as JsMediaTrack).nativeTrack
        val sender = nativePeerConnection.getSenders().find { it.track == mediaTrack }
        sender?.let { nativePeerConnection.removeTrack(it) }
    }

    override suspend fun removeTrack(sender: WebRtc.RtpSender) {
        val rtpSender = (sender as JsRtpSender).nativeSender
        nativePeerConnection.removeTrack(rtpSender)
    }

    override fun close() {
        nativePeerConnection.close()
    }

    override fun restartIce() {
        nativePeerConnection.restartIce()
    }
}

public actual object JsWebRtc : WebRtcClientEngineFactory<JsWebRtcEngineConfig> {
    actual override fun create(block: JsWebRtcEngineConfig.() -> Unit): WebRtcEngine =
        JsWebRtcEngine(JsWebRtcEngineConfig().apply(block))
}
