/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.browser.navigator
import io.ktor.client.webrtc.utils.toJs
import kotlinx.coroutines.await
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
            val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await()
            return JsAudioTrack(mediaStream.getAudioTracks()[0])
        }

    override suspend fun createVideoTrack(constraints: WebRtcMedia.VideoTrackConstraints): WebRtcMedia.VideoTrack =
        withPermissionException("video") {
            val streamConstrains = MediaStreamConstraints(video = constraints.toJs())
            val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await()
            return JsVideoTrack(mediaStream.getVideoTracks()[0])
        }
}

/**
 * Wrapper for MediaStreamTrack.
 **/
public abstract class JsMediaTrack(
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
        public fun from(nativeTrack: MediaStreamTrack): JsMediaTrack =
            when (nativeTrack.kind.toTrackKind()) {
                WebRtcMedia.TrackType.AUDIO -> JsAudioTrack(nativeTrack)
                WebRtcMedia.TrackType.VIDEO -> JsVideoTrack(nativeTrack)
            }
    }
}

public class JsAudioTrack(nativeTrack: MediaStreamTrack) :
    WebRtcMedia.AudioTrack, JsMediaTrack(nativeTrack)

public class JsVideoTrack(nativeTrack: MediaStreamTrack) :
    WebRtcMedia.VideoTrack, JsMediaTrack(nativeTrack)

/**
 * Returns implementation of the native media stream track used under the hood. Use it with caution.
 */
public fun WebRtcMedia.Track.getNative(): MediaStreamTrack {
    val track = this as? JsMediaTrack ?: error("Wrong track implementation.")
    return track.nativeTrack
}
