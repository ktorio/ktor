/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import web.audio.AudioContext
import web.audio.MediaStreamAudioDestinationNode
import web.canvas.CanvasRenderingContext2D
import web.canvas.ID
import web.dom.document
import web.html.HTMLCanvasElement

object MockMediaTrackFactory : MediaTrackFactory {
    private var allowVideo = false
    private var allowAudio = false

    fun grantPermissions(audio: Boolean, video: Boolean) {
        allowAudio = audio
        allowVideo = video
    }

    override suspend fun createAudioTrack(constraints: WebRtcMedia.AudioTrackConstraints): WebRtcMedia.AudioTrack {
        if (!allowAudio) throw WebRtcMedia.PermissionException("audio")
        return makeDummyAudioStreamTrack()
    }

    override suspend fun createVideoTrack(constraints: WebRtcMedia.VideoTrackConstraints): WebRtcMedia.VideoTrack {
        if (!allowVideo) throw WebRtcMedia.PermissionException("video")
        return makeDummyVideoStreamTrack(constraints.width ?: 100, constraints.height ?: 100)
    }
}

// Record silence as an audio track
fun makeDummyAudioStreamTrack(): WebRtcMedia.AudioTrack {
    val ctx = AudioContext()
    val oscillator = ctx.createOscillator()

    val dst = oscillator.connect(ctx.createMediaStreamDestination()) as MediaStreamAudioDestinationNode
    oscillator.start()

    val track = dst.stream.getAudioTracks()[0] ?: error("Failed to create an audio track")
    return JsAudioTrack(track)
}

// Capture canvas as a video track
fun makeDummyVideoStreamTrack(width: Int, height: Int): WebRtcMedia.VideoTrack {
    val canvas = (document.createElement("canvas") as HTMLCanvasElement)
    canvas.width = width
    canvas.height = height

    val ctx = canvas.getContext(CanvasRenderingContext2D.ID) ?: error("Failed to create a canvas context")
    ctx.fillRect(0.0, 0.0, width.toDouble(), height.toDouble())

    val stream = canvas.captureStream()
    val track = stream.getVideoTracks()[0] ?: error("Failed to create a video track")
    return JsVideoTrack(track)
}
