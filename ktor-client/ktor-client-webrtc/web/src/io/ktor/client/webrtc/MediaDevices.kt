/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.webrtc

import js.objects.unsafeJso
import web.mediadevices.getUserMedia
import web.mediastreams.MediaStreamConstraints
import web.mediastreams.MediaStreamTrack
import web.mediastreams.MediaTrackConstraints
import web.navigator.navigator
import kotlin.js.toArray
import kotlin.js.undefined

private fun makeStreamConstraints(
    audio: MediaTrackConstraints? = undefined,
    video: MediaTrackConstraints? = undefined
): MediaStreamConstraints {
    return unsafeJso {
        this.audio = audio
        this.video = video
    }
}

/**
 * MediaTrackFactory based on browser Navigator MediaDevices.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.NavigatorMediaDevices)
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices">MDN MediaDevices</a>
 **/
public object NavigatorMediaDevices : MediaTrackFactory {
    override suspend fun createAudioTrack(constraints: WebRtcMedia.AudioTrackConstraints): WebRtcMedia.AudioTrack =
        withPermissionException("audio") {
            val streamConstrains = makeStreamConstraints(audio = constraints.toJs())
            val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains)
            val tracks = mediaStream.getAudioTracks().toArray()
            if (tracks.isEmpty()) {
                throw WebRtcMedia.DeviceException("Failed to create an audio track.")
            }
            if (tracks.size > 1) {
                println("Warning: more than one audio track created")
            }
            JsAudioTrack(tracks[0])
        }

    override suspend fun createVideoTrack(constraints: WebRtcMedia.VideoTrackConstraints): WebRtcMedia.VideoTrack =
        withPermissionException("video") {
            val streamConstrains = makeStreamConstraints(video = constraints.toJs())
            val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains)
            val tracks = mediaStream.getVideoTracks().toArray()
            if (tracks.isEmpty()) {
                throw WebRtcMedia.DeviceException("Failed to create a video track.")
            }
            if (tracks.size > 1) {
                println("Warning: more than one video track created.")
            }
            return JsVideoTrack(tracks[0])
        }
}

/**
 * Wrapper for MediaStreamTrack.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.JsMediaTrack)
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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.getNative)
 */
public fun WebRtcMedia.Track.getNative(): MediaStreamTrack {
    return (this as JsMediaTrack).nativeTrack
}
