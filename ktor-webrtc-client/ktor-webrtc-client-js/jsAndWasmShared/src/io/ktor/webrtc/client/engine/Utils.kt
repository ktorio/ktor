/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client.engine

import io.ktor.webrtc.client.*

public fun FacingMode.toJs(): String = when (this) {
    FacingMode.USER -> "user"
    FacingMode.LEFT -> "left"
    FacingMode.RIGHT -> "right"
    FacingMode.ENVIRONMENT -> "environment"
}

public fun ResizeMode.toJs(): String = when (this) {
    ResizeMode.NONE -> "none"
    ResizeMode.CROP_AND_SCALE -> "crop-and-scale"
}
