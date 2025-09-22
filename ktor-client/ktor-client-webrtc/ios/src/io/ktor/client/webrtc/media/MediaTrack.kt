/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.media

import WebRTC.RTCAudioTrack
import WebRTC.RTCMediaStreamTrack
import WebRTC.RTCVideoTrack
import io.ktor.client.webrtc.*
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
public abstract class IosMediaTrack(
    internal val nativeTrack: RTCMediaStreamTrack,
    private val onDispose: () -> Unit
) : WebRtcMedia.Track {
    public override val id: String = nativeTrack.trackId

    public override val kind: WebRtcMedia.TrackType = kindOf(nativeTrack)

    public override val enabled: Boolean
        get() = nativeTrack.isEnabled

    override fun enable(enabled: Boolean) {
        nativeTrack.setIsEnabled(enabled)
    }

    override fun close() {
        onDispose()
        nativeTrack.finalize()
    }

    public companion object {
        private fun kindOf(nativeTrack: RTCMediaStreamTrack): WebRtcMedia.TrackType {
            return when (nativeTrack.kind()) {
                "audio" -> WebRtcMedia.TrackType.AUDIO
                "video" -> WebRtcMedia.TrackType.VIDEO
                else -> error("Unknown media track kind: ${nativeTrack.kind()}")
            }
        }

        public fun from(nativeTrack: RTCMediaStreamTrack): IosMediaTrack = when (kindOf(nativeTrack)) {
            WebRtcMedia.TrackType.AUDIO -> IosAudioTrack(nativeTrack)
            WebRtcMedia.TrackType.VIDEO -> IosVideoTrack(nativeTrack)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
public class IosAudioTrack(nativeTrack: RTCMediaStreamTrack, onDispose: () -> Unit = {}) : WebRtcMedia.AudioTrack,
    IosMediaTrack(nativeTrack, onDispose)

@OptIn(ExperimentalForeignApi::class)
public class IosVideoTrack(nativeTrack: RTCMediaStreamTrack, onDispose: () -> Unit = {}) : WebRtcMedia.VideoTrack,
    IosMediaTrack(nativeTrack, onDispose)

/**
 * Returns implementation of the native video stream track used under the hood. Use it with caution.
 */
@OptIn(ExperimentalForeignApi::class)
public fun WebRtcMedia.VideoTrack.getNative(): RTCVideoTrack {
    val track = this as? IosMediaTrack ?: error("Wrong track implementation.")
    return track.nativeTrack as RTCVideoTrack
}

/**
 * Returns implementation of the native audio stream track used under the hood. Use it with caution.
 */
@OptIn(ExperimentalForeignApi::class)
public fun WebRtcMedia.AudioTrack.getNative(): RTCAudioTrack {
    val track = this as? IosMediaTrack ?: error("Wrong track implementation.")
    return track.nativeTrack as RTCAudioTrack
}
