/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.rs

import io.ktor.client.webrtc.*
import uniffi.ktor_client_webrtc.MediaKind
import uniffi.ktor_client_webrtc.MediaStreamTrack

/**
 * Wrapper for uniffi.ktor_client_webrtc.MediaStreamTrack.
 **/
public abstract class RustMediaTrack(
    internal val inner: MediaStreamTrack,
) : WebRtcMedia.Track {
    public override val id: String = inner.id()

    public override val kind: WebRtcMedia.TrackType = kindOf(inner)

    public override val enabled: Boolean
        get() = inner.enabled()

    override fun enable(enabled: Boolean) {
        inner.setEnabled(enabled)
    }

    override fun close() {
        inner.destroy()
    }

    public companion object {
        private fun kindOf(nativeTrack: MediaStreamTrack): WebRtcMedia.TrackType {
            return when (nativeTrack.kind()) {
                MediaKind.AUDIO -> WebRtcMedia.TrackType.AUDIO
                MediaKind.VIDEO -> WebRtcMedia.TrackType.VIDEO
            }
        }

        public fun from(nativeTrack: MediaStreamTrack): RustMediaTrack = when (kindOf(nativeTrack)) {
            WebRtcMedia.TrackType.AUDIO -> RustAudioTrack(nativeTrack)
            WebRtcMedia.TrackType.VIDEO -> RustVideoTrack(nativeTrack)
        }
    }
}

public class RustAudioTrack(nativeTrack: MediaStreamTrack) : WebRtcMedia.AudioTrack, RustMediaTrack(nativeTrack)

public class RustVideoTrack(nativeTrack: MediaStreamTrack) : WebRtcMedia.VideoTrack, RustMediaTrack(nativeTrack)

/**
 * Returns implementation of the native video stream track used under the hood. Use it with caution.
 */
public fun WebRtcMedia.Track.getNative(): MediaStreamTrack {
    val track = this as? RustMediaTrack ?: error("Wrong track implementation.")
    return track.inner
}
