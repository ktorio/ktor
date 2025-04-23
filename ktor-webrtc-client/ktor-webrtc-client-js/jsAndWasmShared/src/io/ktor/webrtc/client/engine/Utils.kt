/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client.engine

import io.ktor.webrtc.client.*

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
