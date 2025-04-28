/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

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
        }, sdp
    )
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

