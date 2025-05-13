/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

public fun WebRTCMedia.FacingMode.toJs(): String = when (this) {
    WebRTCMedia.FacingMode.USER -> "user"
    WebRTCMedia.FacingMode.LEFT -> "left"
    WebRTCMedia.FacingMode.RIGHT -> "right"
    WebRTCMedia.FacingMode.ENVIRONMENT -> "environment"
}

public fun WebRTCMedia.ResizeMode.toJs(): String = when (this) {
    WebRTCMedia.ResizeMode.NONE -> "none"
    WebRTCMedia.ResizeMode.CROP_AND_SCALE -> "crop-and-scale"
}

public fun String.toTrackKind(): WebRTCMedia.TrackType = when (this) {
    "audio" -> WebRTCMedia.TrackType.AUDIO
    "video" -> WebRTCMedia.TrackType.VIDEO
    else -> error("Unknown media track kind: $this")
}

public fun String?.toDegradationPreference(): WebRTC.DegradationPreference = when (this) {
    "maintain-resolution" -> WebRTC.DegradationPreference.MAINTAIN_RESOLUTION
    "maintain-framerate" -> WebRTC.DegradationPreference.MAINTAIN_FRAMERATE
    "balanced" -> WebRTC.DegradationPreference.BALANCED
    null -> WebRTC.DegradationPreference.DISABLED
    else -> error("Unknown degradation type: $this")
}

public fun String?.toIceConnectionState(): WebRTC.IceConnectionState = when (this) {
    "new" -> WebRTC.IceConnectionState.NEW
    "checking" -> WebRTC.IceConnectionState.CHECKING
    "connected" -> WebRTC.IceConnectionState.CONNECTED
    "completed" -> WebRTC.IceConnectionState.COMPLETED
    "failed" -> WebRTC.IceConnectionState.FAILED
    "disconnected" -> WebRTC.IceConnectionState.DISCONNECTED
    "closed" -> WebRTC.IceConnectionState.CLOSED
    else -> error("Unknown ice connection state: $this")
}

public fun String?.toConnectionState(): WebRTC.ConnectionState = when (this) {
    "new" -> WebRTC.ConnectionState.NEW
    "connecting" -> WebRTC.ConnectionState.CONNECTING
    "connected" -> WebRTC.ConnectionState.CONNECTED
    "disconnected" -> WebRTC.ConnectionState.DISCONNECTED
    "closed" -> WebRTC.ConnectionState.CLOSED
    "failed" -> WebRTC.ConnectionState.FAILED
    else -> error("Unknown connection state: $this")
}

public fun String?.toIceGatheringState(): WebRTC.IceGatheringState = when (this) {
    "new" -> WebRTC.IceGatheringState.NEW
    "gathering" -> WebRTC.IceGatheringState.GATHERING
    "complete" -> WebRTC.IceGatheringState.COMPLETE
    else -> error("Unknown ice gathering state: $this")
}

public fun String?.toSignalingState(): WebRTC.SignalingState = when (this) {
    "stable" -> WebRTC.SignalingState.STABLE
    "have-local-offer" -> WebRTC.SignalingState.HAVE_LOCAL_OFFER
    "have-remote-offer" -> WebRTC.SignalingState.HAVE_REMOTE_OFFER
    "have-local-pranswer" -> WebRTC.SignalingState.HAVE_LOCAL_PROVISIONAL_ANSWER
    "have-remote-pranswer" -> WebRTC.SignalingState.HAVE_REMOTE_PROVISIONAL_ANSWER
    "closed" -> WebRTC.SignalingState.CLOSED
    else -> error("Unknown signaling state: $this")
}

internal inline fun <T> withSdpException(message: String, block: () -> T): T {
    try {
        return block()
    } catch (e: Throwable) {
        throw WebRTC.SdpException(message, e)
    }
}

internal inline fun <T> withIceException(message: String, block: () -> T): T {
    try {
        return block()
    } catch (e: Throwable) {
        throw WebRTC.IceException(message, e)
    }
}
