/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.rs

import io.ktor.client.webrtc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uniffi.ktor_client_webrtc.MediaCodec
import uniffi.ktor_client_webrtc.MediaHandler
import uniffi.ktor_client_webrtc.MediaStreamTrack

/**
 * Wrapper for uniffi.ktor_client_webrtc.MediaStreamTrack.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.rs.RustMediaTrack)
 **/
public abstract class RustMediaTrack(
    internal val inner: MediaStreamTrack,
    private val coroutineScope: CoroutineScope?,
) : WebRtcMedia.Track {
    private var readRtpJob: Job? = null

    public override val id: String
        get() = inner.id()

    public override val kind: WebRtcMedia.TrackType
        get() = kindOf(inner)

    public override val enabled: Boolean
        get() = inner.enabled()

    override fun enable(enabled: Boolean) {
        inner.setEnabled(enabled)
    }

    public fun setMediaHandler(handler: MediaHandler, readPacketsInBackground: Boolean) {
        inner.setSink(inner.createSink(handler))
        if (readPacketsInBackground) {
            require(coroutineScope != null) {
                "Coroutine scope is required to read RTP for track $id"
            }
            readRtpJob = coroutineScope.launch { inner.readAll() }
        }
    }

    override fun close() {
        if (readRtpJob?.isActive == true) {
            readRtpJob?.cancel()
        }
        inner.destroy()
    }

    public companion object {
        private fun kindOf(nativeTrack: MediaStreamTrack): WebRtcMedia.TrackType {
            return when (nativeTrack.codec()) {
                MediaCodec.VIDEO_VP8 -> WebRtcMedia.TrackType.VIDEO
                MediaCodec.VIDEO_H264 -> WebRtcMedia.TrackType.VIDEO
                MediaCodec.AUDIO_OPUS -> WebRtcMedia.TrackType.AUDIO
            }
        }

        public fun from(
            nativeTrack: MediaStreamTrack,
            coroutineScope: CoroutineScope?,
        ): RustMediaTrack = when (kindOf(nativeTrack)) {
            WebRtcMedia.TrackType.AUDIO -> RustAudioTrack(nativeTrack, coroutineScope)
            WebRtcMedia.TrackType.VIDEO -> RustVideoTrack(nativeTrack, coroutineScope)
        }
    }
}

public class RustAudioTrack(
    nativeTrack: MediaStreamTrack,
    coroutineScope: CoroutineScope?,
) : WebRtcMedia.AudioTrack, RustMediaTrack(nativeTrack, coroutineScope)

public class RustVideoTrack(
    nativeTrack: MediaStreamTrack,
    coroutineScope: CoroutineScope?,
) : WebRtcMedia.VideoTrack, RustMediaTrack(nativeTrack, coroutineScope)

/**
 * Returns implementation of the native video stream track used under the hood. Use it with caution.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.rs.getNative)
 */
public fun WebRtcMedia.Track.getNative(): MediaStreamTrack {
    val track = this as? RustMediaTrack ?: error("Wrong track implementation.")
    return track.inner
}
