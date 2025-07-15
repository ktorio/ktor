/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.rs

import io.ktor.client.webrtc.*
import uniffi.ktor_client_webrtc.*

internal fun SessionDescription.toKtor() = WebRtc.SessionDescription(
    sdp = sdp,
    type = when (sdpType) {
        SessionDescriptionType.OFFER -> WebRtc.SessionDescriptionType.OFFER
        SessionDescriptionType.ANSWER -> WebRtc.SessionDescriptionType.ANSWER
        SessionDescriptionType.ROLLBACK -> WebRtc.SessionDescriptionType.ROLLBACK
        SessionDescriptionType.PROVISIONAL_ANSWER -> WebRtc.SessionDescriptionType.PROVISIONAL_ANSWER
    }
)

internal fun WebRtc.SessionDescription.toRust() = SessionDescription(
    sdp = sdp,
    sdpType = when (type) {
        WebRtc.SessionDescriptionType.OFFER -> SessionDescriptionType.OFFER
        WebRtc.SessionDescriptionType.ANSWER -> SessionDescriptionType.ANSWER
        WebRtc.SessionDescriptionType.ROLLBACK -> SessionDescriptionType.ROLLBACK
        WebRtc.SessionDescriptionType.PROVISIONAL_ANSWER -> SessionDescriptionType.PROVISIONAL_ANSWER
    }
)

internal fun IceCandidate.toKtor() = WebRtc.IceCandidate(
    candidate = candidate,
    sdpMid = sdpMid ?: error("sdpMid is null"),
    sdpMLineIndex = sdpMlineIndex?.toInt() ?: error("sdpMLineIndex")
)

internal fun WebRtc.IceCandidate.toRust() = IceCandidate(
    sdpMid = sdpMid,
    candidate = candidate,
    sdpMlineIndex = sdpMLineIndex.toUShort()
)

internal fun ConnectionState.toKtor() = when (this) {
    ConnectionState.NEW -> WebRtc.ConnectionState.NEW
    ConnectionState.CONNECTING -> WebRtc.ConnectionState.CONNECTING
    ConnectionState.CONNECTED -> WebRtc.ConnectionState.CONNECTED
    ConnectionState.DISCONNECTED -> WebRtc.ConnectionState.DISCONNECTED
    ConnectionState.FAILED -> WebRtc.ConnectionState.FAILED
    ConnectionState.CLOSED -> WebRtc.ConnectionState.CLOSED
}

internal fun IceConnectionState.toKtor() = when (this) {
    IceConnectionState.NEW -> WebRtc.IceConnectionState.NEW
    IceConnectionState.CHECKING -> WebRtc.IceConnectionState.CHECKING
    IceConnectionState.CONNECTED -> WebRtc.IceConnectionState.CONNECTED
    IceConnectionState.COMPLETED -> WebRtc.IceConnectionState.COMPLETED
    IceConnectionState.FAILED -> WebRtc.IceConnectionState.FAILED
    IceConnectionState.DISCONNECTED -> WebRtc.IceConnectionState.DISCONNECTED
    IceConnectionState.CLOSED -> WebRtc.IceConnectionState.CLOSED
}

internal fun IceGatheringState.toKtor() = when (this) {
    IceGatheringState.NEW -> WebRtc.IceGatheringState.NEW
    IceGatheringState.GATHERING -> WebRtc.IceGatheringState.GATHERING
    IceGatheringState.COMPLETE -> WebRtc.IceGatheringState.COMPLETE
}

internal fun SignalingState.toKtor() = when (this) {
    SignalingState.STABLE -> WebRtc.SignalingState.STABLE
    SignalingState.CLOSED -> WebRtc.SignalingState.CLOSED
    SignalingState.HAVE_LOCAL_OFFER -> WebRtc.SignalingState.HAVE_LOCAL_OFFER
    SignalingState.HAVE_REMOTE_OFFER -> WebRtc.SignalingState.HAVE_REMOTE_OFFER
    SignalingState.HAVE_LOCAL_PROVISIONAL_ANSWER -> WebRtc.SignalingState.HAVE_LOCAL_PROVISIONAL_ANSWER
    SignalingState.HAVE_REMOTE_PROVISIONAL_ANSWER -> WebRtc.SignalingState.HAVE_LOCAL_PROVISIONAL_ANSWER
}

internal fun DataChannelState.toKtor() = when (this) {
    DataChannelState.CONNECTING -> WebRtc.DataChannel.State.CONNECTING
    DataChannelState.OPEN -> WebRtc.DataChannel.State.OPEN
    DataChannelState.CLOSING -> WebRtc.DataChannel.State.CLOSING
    DataChannelState.CLOSED -> WebRtc.DataChannel.State.CLOSED
}

internal fun Stats.toKtor() = WebRtc.Stats(
    id = id,
    type = type,
    timestamp = timestamp.toLong(),
    props = mapOf("native-stats" to props)
)

internal suspend inline fun withSdpException(crossinline block: suspend () -> Unit) {
    try {
        block()
    } catch (e: RtcException.SdpException) {
        throw WebRtc.SdpException(e.v1, e.cause)
    }
}

internal suspend inline fun withIceException(crossinline block: suspend () -> Unit) {
    try {
        block()
    } catch (e: RtcException.IceException) {
        throw WebRtc.IceException(e.v1, e.cause)
    }
}
