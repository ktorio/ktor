/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.media

import io.ktor.client.webrtc.*
import org.webrtc.AudioTrack
import org.webrtc.MediaStreamTrack
import org.webrtc.VideoTrack

/**
 * Wrapper for org.webrtc.MediaStreamTrack.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.media.AndroidMediaTrack)
 **/
public abstract class AndroidMediaTrack(
    internal val nativeTrack: MediaStreamTrack,
    private val onDispose: (() -> Unit)?
) : WebRtcMedia.Track {
    public override val id: String = nativeTrack.id()

    public override val kind: WebRtcMedia.TrackType = kindOf(nativeTrack)

    public override val enabled: Boolean
        get() = nativeTrack.enabled()

    override fun enable(enabled: Boolean) {
        nativeTrack.setEnabled(enabled)
    }

    override fun close() {
        onDispose?.invoke()
        nativeTrack.dispose()
    }

    public companion object {
        private fun kindOf(nativeTrack: MediaStreamTrack): WebRtcMedia.TrackType {
            return when (nativeTrack.kind()) {
                "audio" -> WebRtcMedia.TrackType.AUDIO
                "video" -> WebRtcMedia.TrackType.VIDEO
                else -> error("Unknown media track kind: ${nativeTrack.kind()}")
            }
        }

        public fun from(nativeTrack: MediaStreamTrack): AndroidMediaTrack = when (kindOf(nativeTrack)) {
            WebRtcMedia.TrackType.AUDIO -> AndroidAudioTrack(nativeTrack)
            WebRtcMedia.TrackType.VIDEO -> AndroidVideoTrack(nativeTrack)
        }
    }
}

public class AndroidAudioTrack(nativeTrack: MediaStreamTrack, onDispose: (() -> Unit)? = null) : WebRtcMedia.AudioTrack,
    AndroidMediaTrack(nativeTrack, onDispose)

public class AndroidVideoTrack(nativeTrack: MediaStreamTrack, onDispose: (() -> Unit)? = null) : WebRtcMedia.VideoTrack,
    AndroidMediaTrack(nativeTrack, onDispose)

/**
 * Returns implementation of the native video stream track used under the hood. Use it with caution.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.media.getNative)
 */
public fun WebRtcMedia.VideoTrack.getNative(): VideoTrack {
    return (this as AndroidMediaTrack).nativeTrack as VideoTrack
}

/**
 * Returns implementation of the native audio stream track used under the hood. Use it with caution.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.media.getNative)
 */
public fun WebRtcMedia.AudioTrack.getNative(): AudioTrack {
    return (this as AndroidMediaTrack).nativeTrack as AudioTrack
}
