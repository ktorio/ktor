/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.media

import io.ktor.client.webrtc.MediaTrackFactory
import io.ktor.client.webrtc.WebRtcMedia
import io.ktor.client.webrtc.toArray
import io.ktor.client.webrtc.toJs
import io.ktor.client.webrtc.toTrackKind
import io.ktor.client.webrtc.withPermissionException
import web.mediastreams.MediaStreamConstraints
import web.mediastreams.MediaStreamTrack
import web.mediastreams.MediaTrackConstraints
import web.navigator.navigator
import kotlin.js.undefined

private fun makeStreamConstraints(
    audio: MediaTrackConstraints? = undefined,
    video: MediaTrackConstraints? = undefined
): MediaStreamConstraints {
    @Suppress("NON_EXTERNAL_TYPE_EXTENDS_EXTERNAL_TYPE")
    return object : MediaStreamConstraints {
        override val audio = audio
        override val video = video
        override val peerIdentity = undefined
        override val preferCurrentTab = undefined
    }
}

/**
 * MediaTrackFactory based on browser Navigator MediaDevices.
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices">MDN MediaDevices</a>
 **/
public object NavigatorMediaDevices : MediaTrackFactory {
    override suspend fun createAudioTrack(constraints: WebRtcMedia.AudioTrackConstraints): WebRtcMedia.AudioTrack =
        withPermissionException("audio") {
            val streamConstrains = makeStreamConstraints(audio = constraints.toJs())
            val mediaStream = navigator.mediaDevices.getUserMediaAsync(streamConstrains).await()
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
            val mediaStream = navigator.mediaDevices.getUserMediaAsync(streamConstrains).await()
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
 **/
public abstract class JsMediaTrack(internal val nativeTrack: MediaStreamTrack) : WebRtcMedia.Track {
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
        public fun from(nativeTrack: MediaStreamTrack): JsMediaTrack = when (nativeTrack.kind.toTrackKind()) {
            WebRtcMedia.TrackType.AUDIO -> JsAudioTrack(nativeTrack)
            WebRtcMedia.TrackType.VIDEO -> JsVideoTrack(nativeTrack)
        }
    }
}

public class JsAudioTrack(nativeTrack: MediaStreamTrack) :
    WebRtcMedia.AudioTrack, JsMediaTrack(nativeTrack)

public class JsVideoTrack(nativeTrack: MediaStreamTrack) :
    WebRtcMedia.VideoTrack, JsMediaTrack(nativeTrack)

public fun WebRtcMedia.Track.getNative(): MediaStreamTrack {
    val track = (this as? JsMediaTrack) ?: error("JsMediaTrack is only supported")
    return track.nativeTrack
}
