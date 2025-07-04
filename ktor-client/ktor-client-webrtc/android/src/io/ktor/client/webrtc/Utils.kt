/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.RTCStatsReport
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun WebRtc.SessionDescription.toNative(): SessionDescription {
    return SessionDescription(
        when (type) {
            WebRtc.SessionDescriptionType.OFFER -> SessionDescription.Type.OFFER
            WebRtc.SessionDescriptionType.ANSWER -> SessionDescription.Type.ANSWER
            WebRtc.SessionDescriptionType.ROLLBACK -> SessionDescription.Type.ROLLBACK
            WebRtc.SessionDescriptionType.PROVISIONAL_ANSWER -> SessionDescription.Type.PRANSWER
        },
        sdp
    )
}

internal fun WebRtc.BundlePolicy.toNative(): PeerConnection.BundlePolicy = when (this) {
    WebRtc.BundlePolicy.BALANCED -> PeerConnection.BundlePolicy.BALANCED
    WebRtc.BundlePolicy.MAX_BUNDLE -> PeerConnection.BundlePolicy.MAXBUNDLE
    WebRtc.BundlePolicy.MAX_COMPAT -> PeerConnection.BundlePolicy.MAXCOMPAT
}

internal fun WebRtc.IceTransportPolicy.toNative(): PeerConnection.IceTransportsType = when (this) {
    WebRtc.IceTransportPolicy.ALL -> PeerConnection.IceTransportsType.ALL
    WebRtc.IceTransportPolicy.RELAY -> PeerConnection.IceTransportsType.RELAY
}

internal fun WebRtc.RtcpMuxPolicy.toNative(): PeerConnection.RtcpMuxPolicy = when (this) {
    WebRtc.RtcpMuxPolicy.NEGOTIATE -> PeerConnection.RtcpMuxPolicy.NEGOTIATE
    WebRtc.RtcpMuxPolicy.REQUIRE -> PeerConnection.RtcpMuxPolicy.REQUIRE
}

internal fun PeerConnection.IceConnectionState?.toCommon(): WebRtc.IceConnectionState? = when (this) {
    PeerConnection.IceConnectionState.NEW -> WebRtc.IceConnectionState.NEW
    PeerConnection.IceConnectionState.FAILED -> WebRtc.IceConnectionState.FAILED
    PeerConnection.IceConnectionState.CLOSED -> WebRtc.IceConnectionState.CLOSED
    PeerConnection.IceConnectionState.CHECKING -> WebRtc.IceConnectionState.CHECKING
    PeerConnection.IceConnectionState.CONNECTED -> WebRtc.IceConnectionState.CONNECTED
    PeerConnection.IceConnectionState.COMPLETED -> WebRtc.IceConnectionState.COMPLETED
    PeerConnection.IceConnectionState.DISCONNECTED -> WebRtc.IceConnectionState.DISCONNECTED
    else -> null
}

internal fun PeerConnection.PeerConnectionState?.toCommon(): WebRtc.ConnectionState? = when (this) {
    PeerConnection.PeerConnectionState.NEW -> WebRtc.ConnectionState.NEW
    PeerConnection.PeerConnectionState.CLOSED -> WebRtc.ConnectionState.CLOSED
    PeerConnection.PeerConnectionState.CONNECTED -> WebRtc.ConnectionState.CONNECTED
    PeerConnection.PeerConnectionState.DISCONNECTED -> WebRtc.ConnectionState.DISCONNECTED
    PeerConnection.PeerConnectionState.FAILED -> WebRtc.ConnectionState.FAILED
    PeerConnection.PeerConnectionState.CONNECTING -> WebRtc.ConnectionState.CONNECTING
    else -> null
}

internal fun PeerConnection.SignalingState?.toCommon(): WebRtc.SignalingState? = when (this) {
    PeerConnection.SignalingState.STABLE -> WebRtc.SignalingState.STABLE
    PeerConnection.SignalingState.CLOSED -> WebRtc.SignalingState.CLOSED
    PeerConnection.SignalingState.HAVE_LOCAL_OFFER -> WebRtc.SignalingState.HAVE_LOCAL_OFFER
    PeerConnection.SignalingState.HAVE_LOCAL_PRANSWER -> WebRtc.SignalingState.HAVE_LOCAL_PROVISIONAL_ANSWER
    PeerConnection.SignalingState.HAVE_REMOTE_OFFER -> WebRtc.SignalingState.HAVE_REMOTE_OFFER
    PeerConnection.SignalingState.HAVE_REMOTE_PRANSWER -> WebRtc.SignalingState.HAVE_REMOTE_PROVISIONAL_ANSWER
    else -> null
}

internal fun PeerConnection.IceGatheringState?.toCommon(): WebRtc.IceGatheringState? = when (this) {
    PeerConnection.IceGatheringState.NEW -> WebRtc.IceGatheringState.NEW
    PeerConnection.IceGatheringState.COMPLETE -> WebRtc.IceGatheringState.COMPLETE
    PeerConnection.IceGatheringState.GATHERING -> WebRtc.IceGatheringState.GATHERING
    else -> null
}

internal fun SessionDescription.toCommon(): WebRtc.SessionDescription {
    return WebRtc.SessionDescription(
        when (requireNotNull(type)) {
            SessionDescription.Type.OFFER -> WebRtc.SessionDescriptionType.OFFER
            SessionDescription.Type.ANSWER -> WebRtc.SessionDescriptionType.ANSWER
            SessionDescription.Type.ROLLBACK -> WebRtc.SessionDescriptionType.ROLLBACK
            SessionDescription.Type.PRANSWER -> WebRtc.SessionDescriptionType.PROVISIONAL_ANSWER
        },
        description
    )
}

internal fun RTCStatsReport.toCommon(): List<WebRtc.Stats> = statsMap.values.map {
    WebRtc.Stats(
        it.id,
        it.type,
        it.timestampUs.toLong(),
        it.members
    )
}

internal fun IceCandidate.toCommon(): WebRtc.IceCandidate = WebRtc.IceCandidate(
    candidate = sdp,
    sdpMid = sdpMid,
    sdpMLineIndex = sdpMLineIndex
)

internal fun Continuation<SessionDescription>.resumeAfterSdpCreate(): SdpObserver {
    return object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) = resume(requireNotNull(sdp))
        override fun onCreateFailure(error: String?) = resumeWithException(WebRtc.SdpException(error))
        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}

internal fun Continuation<Unit>.resumeAfterSdpSet(): SdpObserver {
    return object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetSuccess() = resume(Unit)
        override fun onSetFailure(error: String?) = resumeWithException(WebRtc.SdpException(error))
    }
}
