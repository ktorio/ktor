/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.browser.navigator
import io.ktor.client.webrtc.utils.toJs
import kotlinx.coroutines.await
import org.w3c.dom.mediacapture.MediaStream
import org.w3c.dom.mediacapture.MediaStreamConstraints
import org.w3c.dom.mediacapture.MediaStreamTrack

/**
 * MediaTrackFactory based on browser Navigator MediaDevices.
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices">MDN MediaDevices</a>
 **/
public object NavigatorMediaDevices : MediaTrackFactory {
    override suspend fun createAudioTrack(constraints: WebRtcMedia.AudioTrackConstraints): WebRtcMedia.AudioTrack =
        withPermissionException("audio") {
            val streamConstrains = MediaStreamConstraints(audio = constraints.toJs())
            val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await<MediaStream>()
            return WasmJsAudioTrack(mediaStream.getAudioTracks().toArray()[0])
        }

    override suspend fun createVideoTrack(constraints: WebRtcMedia.VideoTrackConstraints): WebRtcMedia.VideoTrack =
        withPermissionException("video") {
            val streamConstrains = MediaStreamConstraints(video = constraints.toJs())
            val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await<MediaStream>()
            return WasmJsVideoTrack(mediaStream.getVideoTracks().toArray()[0])
        }
}

/**
 * Wrapper for MediaStreamTrack.
 **/
public abstract class WasmJsMediaTrack(
    internal val nativeTrack: MediaStreamTrack
) : WebRtcMedia.Track {
    public override val id: String = nativeTrack.id
    public override val kind: WebRtcMedia.TrackType = nativeTrack.kind.toTrackKind()

    public override val enabled: Boolean
        get() = nativeTrack.enabled

    override fun enable(enabled: Boolean) {
        nativeTrack.enabled = enabled
    }

    override fun close() {
        nativeTrack.stop()
    }

    public companion object {
        public fun from(nativeTrack: MediaStreamTrack): WasmJsMediaTrack =
            when (nativeTrack.kind.toTrackKind()) {
                WebRtcMedia.TrackType.AUDIO -> WasmJsAudioTrack(nativeTrack)
                WebRtcMedia.TrackType.VIDEO -> WasmJsVideoTrack(nativeTrack)
            }
    }
}

public class WasmJsAudioTrack(nativeTrack: MediaStreamTrack) :
    WebRtcMedia.AudioTrack, WasmJsMediaTrack(nativeTrack)

public class WasmJsVideoTrack(nativeTrack: MediaStreamTrack) :
    WebRtcMedia.VideoTrack, WasmJsMediaTrack(nativeTrack)

public fun WebRtcMedia.Track.getNative(): MediaStreamTrack {
    val track = this as? WasmJsMediaTrack ?: error("Wrong track implementation.")
    return track.nativeTrack
}
