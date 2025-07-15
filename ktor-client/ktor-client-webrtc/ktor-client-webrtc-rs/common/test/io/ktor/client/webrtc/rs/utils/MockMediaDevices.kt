/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.rs.utils

import io.ktor.client.webrtc.*
import io.ktor.client.webrtc.rs.*
import uniffi.ktor_client_webrtc.CodecMimeType
import uniffi.ktor_client_webrtc.createAudioTrack
import uniffi.ktor_client_webrtc.createVideoTrack

class MockMediaDevices : MediaTrackFactory {
    var id = 0

    override suspend fun createAudioTrack(constraints: WebRtcMedia.AudioTrackConstraints): WebRtcMedia.AudioTrack {
        val nativeTrack = createAudioTrack(
            codecMime = CodecMimeType.AUDIO_OPUS,
            trackId = "ktor-audio-track-${id++}",
            streamId = "ktor-audio-stream"
        )
        return RustAudioTrack(nativeTrack)
    }

    override suspend fun createVideoTrack(constraints: WebRtcMedia.VideoTrackConstraints): WebRtcMedia.VideoTrack {
        val nativeTrack = createVideoTrack(
            codecMime = CodecMimeType.VIDEO_H264,
            trackId = "ktor-video-track-${id++}",
            streamId = "ktor-video-stream"
        )
        return RustVideoTrack(nativeTrack)
    }
}
