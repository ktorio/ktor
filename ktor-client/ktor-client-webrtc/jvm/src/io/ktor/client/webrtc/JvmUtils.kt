/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import dev.onvoid.webrtc.*
import kotlinx.io.bytestring.decodeToString
import kotlinx.io.bytestring.getByteString
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun WebRtc.IceServer.toJvm(): RTCIceServer =
    RTCIceServer().also { server ->
        server.urls = urls.split(",")
        username?.let { server.username = it }
        credential?.let { server.password = it }
    }

internal fun WebRtc.BundlePolicy.toJvm(): RTCBundlePolicy = when (this) {
    WebRtc.BundlePolicy.BALANCED -> RTCBundlePolicy.BALANCED
    WebRtc.BundlePolicy.MAX_BUNDLE -> RTCBundlePolicy.MAX_BUNDLE
    WebRtc.BundlePolicy.MAX_COMPAT -> RTCBundlePolicy.MAX_COMPAT
}

internal fun WebRtc.IceTransportPolicy.toJvm(): RTCIceTransportPolicy = when (this) {
    WebRtc.IceTransportPolicy.ALL -> RTCIceTransportPolicy.ALL
    WebRtc.IceTransportPolicy.RELAY -> RTCIceTransportPolicy.RELAY
}

internal fun WebRtc.RtcpMuxPolicy.toJvm(): RTCRtcpMuxPolicy = when (this) {
    WebRtc.RtcpMuxPolicy.NEGOTIATE -> RTCRtcpMuxPolicy.NEGOTIATE
    WebRtc.RtcpMuxPolicy.REQUIRE -> RTCRtcpMuxPolicy.REQUIRE
}

internal fun RTCIceConnectionState.toKtor(): WebRtc.IceConnectionState = when (this) {
    RTCIceConnectionState.NEW -> WebRtc.IceConnectionState.NEW
    RTCIceConnectionState.CHECKING -> WebRtc.IceConnectionState.CHECKING
    RTCIceConnectionState.CONNECTED -> WebRtc.IceConnectionState.CONNECTED
    RTCIceConnectionState.COMPLETED -> WebRtc.IceConnectionState.COMPLETED
    RTCIceConnectionState.FAILED -> WebRtc.IceConnectionState.FAILED
    RTCIceConnectionState.DISCONNECTED -> WebRtc.IceConnectionState.DISCONNECTED
    RTCIceConnectionState.CLOSED -> WebRtc.IceConnectionState.CLOSED
}

internal fun RTCPeerConnectionState.toKtor(): WebRtc.ConnectionState = when (this) {
    RTCPeerConnectionState.NEW -> WebRtc.ConnectionState.NEW
    RTCPeerConnectionState.CONNECTING -> WebRtc.ConnectionState.CONNECTING
    RTCPeerConnectionState.CONNECTED -> WebRtc.ConnectionState.CONNECTED
    RTCPeerConnectionState.DISCONNECTED -> WebRtc.ConnectionState.DISCONNECTED
    RTCPeerConnectionState.FAILED -> WebRtc.ConnectionState.FAILED
    RTCPeerConnectionState.CLOSED -> WebRtc.ConnectionState.CLOSED
}

internal fun RTCSignalingState.toKtor(): WebRtc.SignalingState = when (this) {
    RTCSignalingState.STABLE -> WebRtc.SignalingState.STABLE
    RTCSignalingState.CLOSED -> WebRtc.SignalingState.CLOSED
    RTCSignalingState.HAVE_LOCAL_OFFER -> WebRtc.SignalingState.HAVE_LOCAL_OFFER
    RTCSignalingState.HAVE_LOCAL_PR_ANSWER -> WebRtc.SignalingState.HAVE_LOCAL_PROVISIONAL_ANSWER
    RTCSignalingState.HAVE_REMOTE_OFFER -> WebRtc.SignalingState.HAVE_REMOTE_OFFER
    RTCSignalingState.HAVE_REMOTE_PR_ANSWER -> WebRtc.SignalingState.HAVE_REMOTE_PROVISIONAL_ANSWER
}

internal fun RTCIceGatheringState.toKtor(): WebRtc.IceGatheringState = when (this) {
    RTCIceGatheringState.NEW -> WebRtc.IceGatheringState.NEW
    RTCIceGatheringState.GATHERING -> WebRtc.IceGatheringState.GATHERING
    RTCIceGatheringState.COMPLETE -> WebRtc.IceGatheringState.COMPLETE
}

internal fun RTCDataChannelState.toKtor(): WebRtc.DataChannel.State = when (this) {
    RTCDataChannelState.CONNECTING -> WebRtc.DataChannel.State.CONNECTING
    RTCDataChannelState.OPEN -> WebRtc.DataChannel.State.OPEN
    RTCDataChannelState.CLOSING -> WebRtc.DataChannel.State.CLOSING
    RTCDataChannelState.CLOSED -> WebRtc.DataChannel.State.CLOSED
}

internal fun RTCDataChannelBuffer.toKtor(): WebRtc.DataChannel.Message =
    if (binary) {
        val bytes = ByteArray(size = data.remaining()).also { data.get(it) }
        WebRtc.DataChannel.Message.Binary(data = bytes)
    } else {
        WebRtc.DataChannel.Message.Text(data.getByteString().decodeToString())
    }

internal fun RTCSessionDescription.toKtor(): WebRtc.SessionDescription = WebRtc.SessionDescription(
    type = when (this.sdpType) {
        RTCSdpType.OFFER -> WebRtc.SessionDescriptionType.OFFER
        RTCSdpType.ANSWER -> WebRtc.SessionDescriptionType.ANSWER
        RTCSdpType.ROLLBACK -> WebRtc.SessionDescriptionType.ROLLBACK
        else -> WebRtc.SessionDescriptionType.PROVISIONAL_ANSWER
    },
    sdp = this.sdp
)

internal fun WebRtc.SessionDescription.toJvm(): RTCSessionDescription = RTCSessionDescription(
    when (this.type) {
        WebRtc.SessionDescriptionType.OFFER -> RTCSdpType.OFFER
        WebRtc.SessionDescriptionType.ANSWER -> RTCSdpType.ANSWER
        WebRtc.SessionDescriptionType.ROLLBACK -> RTCSdpType.ROLLBACK
        WebRtc.SessionDescriptionType.PROVISIONAL_ANSWER -> RTCSdpType.PR_ANSWER
    },
    this.sdp
)

internal fun RTCIceCandidate.toKtor(): WebRtc.IceCandidate = WebRtc.IceCandidate(
    candidate = sdp,
    sdpMid = sdpMid,
    sdpMLineIndex = sdpMLineIndex
)

internal fun WebRtc.IceCandidate.toJvm(): RTCIceCandidate = RTCIceCandidate(
    sdpMid,
    sdpMLineIndex,
    candidate
)

internal fun RTCStatsReport.toKtor(): List<WebRtc.Stats> = stats.values.map {
    val type = it.type.toString().lowercase().replace('_', '-')
    WebRtc.Stats(it.id, type, it.timestamp, it.attributes)
}

internal fun Continuation<WebRtc.SessionDescription>.resumeAfterSdpCreate(): CreateSessionDescriptionObserver {
    return object : CreateSessionDescriptionObserver {
        override fun onSuccess(description: RTCSessionDescription?) {
            if (description == null) {
                resumeWithException(WebRtc.SdpException("Failed to create session description."))
                return
            }
            resume(description.toKtor())
        }

        override fun onFailure(error: String?) {
            resumeWithException(WebRtc.SdpException(message = error.toString()))
        }
    }
}

internal fun Continuation<Unit>.resumeAfterSdpSet(): SetSessionDescriptionObserver {
    return object : SetSessionDescriptionObserver {
        override fun onSuccess() = resume(Unit)

        override fun onFailure(error: String?) {
            resumeWithException(WebRtc.SdpException(message = error.toString()))
        }
    }
}
