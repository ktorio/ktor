/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.media

import io.ktor.utils.io.InternalKtorApi
import kotlinx.cinterop.ExperimentalForeignApi

@InternalKtorApi
@OptIn(ExperimentalForeignApi::class)
public actual fun defaultVideoCapturerFactory(): VideoCapturerFactory = CameraVideoCapturer.Companion
