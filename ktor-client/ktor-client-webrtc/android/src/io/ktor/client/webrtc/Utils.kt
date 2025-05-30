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

public fun WebRTC.SessionDescription.toNative(): SessionDescription {
    return SessionDescription(
        when (type) {
            WebRTC.SessionDescriptionType.OFFER -> SessionDescription.Type.OFFER
            WebRTC.SessionDescriptionType.ANSWER -> SessionDescription.Type.ANSWER
            WebRTC.SessionDescriptionType.ROLLBACK -> SessionDescription.Type.ROLLBACK
            WebRTC.SessionDescriptionType.PROVISIONAL_ANSWER -> SessionDescription.Type.PRANSWER
        },
        sdp
    )
}

public fun WebRTC.BundlePolicy.toNative(): PeerConnection.BundlePolicy = when (this) {
    WebRTC.BundlePolicy.BALANCED -> PeerConnection.BundlePolicy.BALANCED
    WebRTC.BundlePolicy.MAX_BUNDLE -> PeerConnection.BundlePolicy.MAXBUNDLE
    WebRTC.BundlePolicy.MAX_COMPAT -> PeerConnection.BundlePolicy.MAXCOMPAT
}

public fun WebRTC.IceTransportPolicy.toNative(): PeerConnection.IceTransportsType = when (this) {
    WebRTC.IceTransportPolicy.ALL -> PeerConnection.IceTransportsType.ALL
    WebRTC.IceTransportPolicy.RELAY -> PeerConnection.IceTransportsType.RELAY
}

public fun WebRTC.RTCPMuxPolicy.toNative(): PeerConnection.RtcpMuxPolicy = when (this) {
    WebRTC.RTCPMuxPolicy.NEGOTIATE -> PeerConnection.RtcpMuxPolicy.NEGOTIATE
    WebRTC.RTCPMuxPolicy.REQUIRE -> PeerConnection.RtcpMuxPolicy.REQUIRE
}

public fun PeerConnection.IceConnectionState?.toCommon(): WebRTC.IceConnectionState? = when (this) {
    PeerConnection.IceConnectionState.NEW -> WebRTC.IceConnectionState.NEW
    PeerConnection.IceConnectionState.FAILED -> WebRTC.IceConnectionState.FAILED
    PeerConnection.IceConnectionState.CLOSED -> WebRTC.IceConnectionState.CLOSED
    PeerConnection.IceConnectionState.CHECKING -> WebRTC.IceConnectionState.CHECKING
    PeerConnection.IceConnectionState.CONNECTED -> WebRTC.IceConnectionState.CONNECTED
    PeerConnection.IceConnectionState.COMPLETED -> WebRTC.IceConnectionState.COMPLETED
    PeerConnection.IceConnectionState.DISCONNECTED -> WebRTC.IceConnectionState.DISCONNECTED
    else -> null
}

public fun PeerConnection.PeerConnectionState?.toCommon(): WebRTC.ConnectionState? = when (this) {
    PeerConnection.PeerConnectionState.NEW -> WebRTC.ConnectionState.NEW
    PeerConnection.PeerConnectionState.CLOSED -> WebRTC.ConnectionState.CLOSED
    PeerConnection.PeerConnectionState.CONNECTED -> WebRTC.ConnectionState.CONNECTED
    PeerConnection.PeerConnectionState.DISCONNECTED -> WebRTC.ConnectionState.DISCONNECTED
    PeerConnection.PeerConnectionState.FAILED -> WebRTC.ConnectionState.FAILED
    PeerConnection.PeerConnectionState.CONNECTING -> WebRTC.ConnectionState.CONNECTING
    else -> null
}

public fun PeerConnection.SignalingState?.toCommon(): WebRTC.SignalingState? = when (this) {
    PeerConnection.SignalingState.STABLE -> WebRTC.SignalingState.STABLE
    PeerConnection.SignalingState.CLOSED -> WebRTC.SignalingState.CLOSED
    PeerConnection.SignalingState.HAVE_LOCAL_OFFER -> WebRTC.SignalingState.HAVE_LOCAL_OFFER
    PeerConnection.SignalingState.HAVE_LOCAL_PRANSWER -> WebRTC.SignalingState.HAVE_LOCAL_PROVISIONAL_ANSWER
    PeerConnection.SignalingState.HAVE_REMOTE_OFFER -> WebRTC.SignalingState.HAVE_REMOTE_OFFER
    PeerConnection.SignalingState.HAVE_REMOTE_PRANSWER -> WebRTC.SignalingState.HAVE_REMOTE_PROVISIONAL_ANSWER
    else -> null
}

public fun PeerConnection.IceGatheringState?.toCommon(): WebRTC.IceGatheringState? = when (this) {
    PeerConnection.IceGatheringState.NEW -> WebRTC.IceGatheringState.NEW
    PeerConnection.IceGatheringState.COMPLETE -> WebRTC.IceGatheringState.COMPLETE
    PeerConnection.IceGatheringState.GATHERING -> WebRTC.IceGatheringState.GATHERING
    else -> null
}

public fun SessionDescription.toCommon(): WebRTC.SessionDescription {
    return WebRTC.SessionDescription(
        when (requireNotNull(type)) {
            SessionDescription.Type.OFFER -> WebRTC.SessionDescriptionType.OFFER
            SessionDescription.Type.ANSWER -> WebRTC.SessionDescriptionType.ANSWER
            SessionDescription.Type.ROLLBACK -> WebRTC.SessionDescriptionType.ROLLBACK
            SessionDescription.Type.PRANSWER -> WebRTC.SessionDescriptionType.PROVISIONAL_ANSWER
        },
        description
    )
}

public fun RTCStatsReport.toCommon(): List<WebRTC.Stats> = statsMap.values.map {
    WebRTC.Stats(
        it.id,
        it.type,
        it.timestampUs.toLong(),
        it.members
    )
}

public fun IceCandidate.toCommon(): WebRTC.IceCandidate = WebRTC.IceCandidate(
    candidate = sdp,
    sdpMid = sdpMid,
    sdpMLineIndex = sdpMLineIndex
)

public fun Continuation<SessionDescription>.resumeAfterSdpCreate(): SdpObserver {
    return object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) = resume(requireNotNull(sdp))
        override fun onCreateFailure(error: String?) = resumeWithException(WebRTC.SdpException(error))
        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}

public fun Continuation<Unit>.resumeAfterSdpSet(): SdpObserver {
    return object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetSuccess() = resume(Unit)
        override fun onSetFailure(error: String?) = resumeWithException(WebRTC.SdpException(error))
    }
}
