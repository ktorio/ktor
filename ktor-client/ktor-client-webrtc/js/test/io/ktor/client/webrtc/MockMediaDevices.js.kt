/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.browser.*
import kotlinx.browser.document
import org.w3c.dom.CanvasRenderingContext2D

actual fun makeDummyAudioStreamTrack(): WebRTCMedia.AudioTrack {
    val ctx = AudioContext()
    val oscillator = ctx.createOscillator()
    val dst = oscillator.connect(ctx.createMediaStreamDestination())
    oscillator.start()
    return JsAudioTrack(dst.stream.getAudioTracks()[0], dst.stream)
}

actual fun makeDummyVideoStreamTrack(width: Int, height: Int): WebRTCMedia.VideoTrack {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.width = width
    canvas.height = height
    val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
    ctx.fillRect(0.0, 0.0, width.toDouble(), height.toDouble())
    val stream = canvas.captureStream()
    return JsVideoTrack(stream.getVideoTracks()[0], stream)
}
