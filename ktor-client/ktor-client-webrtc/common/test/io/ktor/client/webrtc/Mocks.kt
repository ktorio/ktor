/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import kotlin.coroutines.CoroutineContext

open class MockWebRtcConnection(context: CoroutineContext, config: WebRtcConnectionConfig) :
    WebRtcPeerConnection(context, config) {
    override val localDescription: WebRtc.SessionDescription? = null
    override val remoteDescription: WebRtc.SessionDescription? = null

    override suspend fun createOffer(): WebRtc.SessionDescription = TODO()

    override suspend fun createAnswer(): WebRtc.SessionDescription = TODO()

    override suspend fun createDataChannel(
        label: String,
        options: WebRtcDataChannelOptions.() -> Unit
    ): WebRtcDataChannel = TODO()

    override fun restartIce() = TODO()
    override suspend fun setLocalDescription(description: WebRtc.SessionDescription) = TODO()
    override suspend fun setRemoteDescription(description: WebRtc.SessionDescription) = TODO()
    override suspend fun addIceCandidate(candidate: WebRtc.IceCandidate) = TODO()
    override suspend fun addTrack(track: WebRtcMedia.Track): WebRtc.RtpSender = TODO()
    override suspend fun removeTrack(sender: WebRtc.RtpSender) = TODO()
    override suspend fun removeTrack(track: WebRtcMedia.Track) = TODO()
    override suspend fun getStatistics(): List<WebRtc.Stats> = TODO()
}

open class MockWebRtcEngine : WebRtcEngineBase("mock-engine") {
    override val config: WebRtcConfig = WebRtcConfig()

    override suspend fun createPeerConnection(config: WebRtcConnectionConfig): WebRtcPeerConnection = TODO()

    override suspend fun createAudioTrack(constraints: WebRtcMedia.AudioTrackConstraints): WebRtcMedia.AudioTrack =
        TODO()

    override suspend fun createVideoTrack(constraints: WebRtcMedia.VideoTrackConstraints): WebRtcMedia.VideoTrack =
        TODO()
}
