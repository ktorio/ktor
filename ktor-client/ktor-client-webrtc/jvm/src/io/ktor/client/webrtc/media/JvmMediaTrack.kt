/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.media

import dev.onvoid.webrtc.media.MediaStreamTrack
import dev.onvoid.webrtc.media.audio.AudioTrack
import dev.onvoid.webrtc.media.video.VideoTrack
import io.ktor.client.webrtc.*

/**
 * JVM implementation of a WebRTC media track wrapper.
 */
public abstract class JvmMediaTrack(
    internal val inner: MediaStreamTrack,
    private val onDispose: () -> Unit
) : WebRtcMedia.Track {
    private var closed = false

    override val id: String = inner.id

    public override val kind: WebRtcMedia.TrackType = kindOf(inner)

    override val enabled: Boolean
        get() = inner.isEnabled

    override fun enable(enabled: Boolean) {
        inner.isEnabled = enabled
    }

    override fun close() {
        if (!closed) {
            closed = true
            onDispose()
        }
    }

    public companion object {
        private fun kindOf(nativeTrack: MediaStreamTrack): WebRtcMedia.TrackType {
            return when (nativeTrack.kind) {
                "audio" -> WebRtcMedia.TrackType.AUDIO
                "video" -> WebRtcMedia.TrackType.VIDEO
                else -> error("Unknown media track kind: ${nativeTrack.kind}")
            }
        }

        public fun from(nativeTrack: MediaStreamTrack): JvmMediaTrack {
            return when (kindOf(nativeTrack)) {
                WebRtcMedia.TrackType.AUDIO -> JvmAudioTrack(nativeTrack)
                WebRtcMedia.TrackType.VIDEO -> JvmVideoTrack(nativeTrack)
            }
        }
    }
}

public class JvmAudioTrack(native: MediaStreamTrack, onDispose: () -> Unit = {}) :
    WebRtcMedia.AudioTrack, JvmMediaTrack(native, onDispose)

public class JvmVideoTrack(native: MediaStreamTrack, onDispose: () -> Unit = {}) :
    WebRtcMedia.VideoTrack, JvmMediaTrack(native, onDispose)

/**
 * Helper to access native tracks. Use with caution.
 */
public fun WebRtcMedia.VideoTrack.getNative(): VideoTrack =
    (this as JvmMediaTrack).inner as VideoTrack

/**
 * Helper to access native tracks. Use with caution.
 */
public fun WebRtcMedia.AudioTrack.getNative(): AudioTrack =
    (this as JvmMediaTrack).inner as AudioTrack
