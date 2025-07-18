/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

// Common utils for the web platform

internal fun WebRtcMedia.FacingMode.toJs(): String = when (this) {
    WebRtcMedia.FacingMode.USER -> "user"
    WebRtcMedia.FacingMode.LEFT -> "left"
    WebRtcMedia.FacingMode.RIGHT -> "right"
    WebRtcMedia.FacingMode.ENVIRONMENT -> "environment"
}

internal fun WebRtcMedia.ResizeMode.toJs(): String = when (this) {
    WebRtcMedia.ResizeMode.NONE -> "none"
    WebRtcMedia.ResizeMode.CROP_AND_SCALE -> "crop-and-scale"
}

internal fun WebRtc.BundlePolicy.toJs(): String = when (this) {
    WebRtc.BundlePolicy.BALANCED -> "balanced"
    WebRtc.BundlePolicy.MAX_BUNDLE -> "max-bundle"
    WebRtc.BundlePolicy.MAX_COMPAT -> "max-compat"
}

internal fun WebRtc.RtcpMuxPolicy.toJs(): String = when (this) {
    WebRtc.RtcpMuxPolicy.NEGOTIATE -> "negotiate"
    WebRtc.RtcpMuxPolicy.REQUIRE -> "require"
}

internal fun WebRtc.IceTransportPolicy.toJs(): String = when (this) {
    WebRtc.IceTransportPolicy.ALL -> "all"
    WebRtc.IceTransportPolicy.RELAY -> "relay"
}

internal fun WebRtc.SessionDescriptionType.toJs(): String = when (this) {
    WebRtc.SessionDescriptionType.OFFER -> "offer"
    WebRtc.SessionDescriptionType.ANSWER -> "answer"
    WebRtc.SessionDescriptionType.ROLLBACK -> "rollback"
    WebRtc.SessionDescriptionType.PROVISIONAL_ANSWER -> "pranswer"
}

internal fun String.toTrackKind(): WebRtcMedia.TrackType = when (this) {
    "audio" -> WebRtcMedia.TrackType.AUDIO
    "video" -> WebRtcMedia.TrackType.VIDEO
    else -> error("Unknown media track kind: $this")
}

internal fun String.toSdpDescriptionType(): WebRtc.SessionDescriptionType = when (this) {
    "offer" -> WebRtc.SessionDescriptionType.OFFER
    "answer" -> WebRtc.SessionDescriptionType.ANSWER
    "pranswer" -> WebRtc.SessionDescriptionType.PROVISIONAL_ANSWER
    "rollback" -> WebRtc.SessionDescriptionType.ROLLBACK
    else -> error("Unknown SDP description type: $this")
}

internal fun String?.toDegradationPreference(): WebRtc.DegradationPreference = when (this) {
    "maintain-resolution" -> WebRtc.DegradationPreference.MAINTAIN_RESOLUTION
    "maintain-framerate" -> WebRtc.DegradationPreference.MAINTAIN_FRAMERATE
    "balanced" -> WebRtc.DegradationPreference.BALANCED
    null -> WebRtc.DegradationPreference.DISABLED
    else -> error("Unknown degradation type: $this")
}

internal fun String?.toIceConnectionState(): WebRtc.IceConnectionState = when (this) {
    "new" -> WebRtc.IceConnectionState.NEW
    "checking" -> WebRtc.IceConnectionState.CHECKING
    "connected" -> WebRtc.IceConnectionState.CONNECTED
    "completed" -> WebRtc.IceConnectionState.COMPLETED
    "failed" -> WebRtc.IceConnectionState.FAILED
    "disconnected" -> WebRtc.IceConnectionState.DISCONNECTED
    "closed" -> WebRtc.IceConnectionState.CLOSED
    else -> error("Unknown ice connection state: $this")
}

internal fun String?.toConnectionState(): WebRtc.ConnectionState = when (this) {
    "new" -> WebRtc.ConnectionState.NEW
    "connecting" -> WebRtc.ConnectionState.CONNECTING
    "connected" -> WebRtc.ConnectionState.CONNECTED
    "disconnected" -> WebRtc.ConnectionState.DISCONNECTED
    "closed" -> WebRtc.ConnectionState.CLOSED
    "failed" -> WebRtc.ConnectionState.FAILED
    else -> error("Unknown connection state: $this")
}

internal fun String?.toIceGatheringState(): WebRtc.IceGatheringState = when (this) {
    "new" -> WebRtc.IceGatheringState.NEW
    "gathering" -> WebRtc.IceGatheringState.GATHERING
    "complete" -> WebRtc.IceGatheringState.COMPLETE
    else -> error("Unknown ice gathering state: $this")
}

internal fun String?.toSignalingState(): WebRtc.SignalingState = when (this) {
    "stable" -> WebRtc.SignalingState.STABLE
    "have-local-offer" -> WebRtc.SignalingState.HAVE_LOCAL_OFFER
    "have-remote-offer" -> WebRtc.SignalingState.HAVE_REMOTE_OFFER
    "have-local-pranswer" -> WebRtc.SignalingState.HAVE_LOCAL_PROVISIONAL_ANSWER
    "have-remote-pranswer" -> WebRtc.SignalingState.HAVE_REMOTE_PROVISIONAL_ANSWER
    "closed" -> WebRtc.SignalingState.CLOSED
    else -> error("Unknown signaling state: $this")
}

internal fun String?.toDataChannelState(): WebRtc.DataChannelState = when (this) {
    "connecting" -> WebRtc.DataChannelState.CONNECTING
    "open" -> WebRtc.DataChannelState.OPEN
    "closing" -> WebRtc.DataChannelState.CLOSING
    "closed" -> WebRtc.DataChannelState.CLOSED
    else -> error("Unknown data channel state: $this")
}

internal inline fun <T> withPermissionException(mediaType: String, block: () -> T): T {
    try {
        return block()
    } catch (e: Throwable) {
        if (e.message?.contains("Permission denied") == true) {
            throw WebRtcMedia.PermissionException(mediaType)
        }
        throw e
    }
}

internal inline fun <T> withSdpException(message: String, block: () -> T): T {
    try {
        return block()
    } catch (e: Throwable) {
        throw WebRtc.SdpException(message, e)
    }
}

internal inline fun <T> withIceException(message: String, block: () -> T): T {
    try {
        return block()
    } catch (e: Throwable) {
        throw WebRtc.IceException(message, e)
    }
}
