/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import org.w3c.dom.mediacapture.MediaStream

fun makeDummyAudioStream(): MediaStream = js(
    """{
        const ctx = new AudioContext();
        const oscillator = ctx.createOscillator();
        const dst = oscillator.connect(ctx.createMediaStreamDestination());
        oscillator.start();
        return dst.stream;
    }"""
)

fun makeDummyVideoStream(width: Int, height: Int): MediaStream = js(
    """{
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext("2d");
        ctx.fillRect(0, 0, width, height);
        const stream = canvas.captureStream();
        return stream;
    }"""
)

actual fun makeDummyAudioStreamTrack(): WebRtcMedia.AudioTrack {
    val stream = makeDummyAudioStream()
    return WasmJsAudioTrack(stream.getAudioTracks()[0]!!, stream)
}

actual fun makeDummyVideoStreamTrack(
    width: Int,
    height: Int
): WebRtcMedia.VideoTrack {
    val stream = makeDummyVideoStream(width, height)
    return WasmJsVideoTrack(stream.getVideoTracks()[0]!!, stream)
}
