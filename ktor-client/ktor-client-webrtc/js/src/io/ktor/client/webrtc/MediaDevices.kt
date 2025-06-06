/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.browser.navigator
import io.ktor.client.webrtc.utils.toJS
import kotlinx.coroutines.await
import org.w3c.dom.mediacapture.MediaStream
import org.w3c.dom.mediacapture.MediaStreamConstraints
import org.w3c.dom.mediacapture.MediaStreamTrack

private inline fun <T> withPermissionException(mediaType: String, block: () -> T): T {
    try {
        return block()
    } catch (e: Throwable) {
        if (e.message?.contains("Permission denied") == true) {
            throw WebRtcMedia.PermissionException(mediaType)
        }
        throw e
    }
}

/**
 * MediaTrackFactory based on browser Navigator MediaDevices.
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices">MDN MediaDevices</a>
 **/
public object NavigatorMediaDevices : MediaTrackFactory {
    override suspend fun createAudioTrack(constraints: WebRtcMedia.AudioTrackConstraints): WebRtcMedia.AudioTrack =
        withPermissionException("audio") {
            val streamConstrains = MediaStreamConstraints(audio = constraints.toJS())
            val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await()
            return JsAudioTrack(mediaStream.getAudioTracks()[0], mediaStream)
        }

    override suspend fun createVideoTrack(constraints: WebRtcMedia.VideoTrackConstraints): WebRtcMedia.VideoTrack =
        withPermissionException("video") {
            val streamConstrains = MediaStreamConstraints(audio = constraints.toJS())
            val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await()
            return JsVideoTrack(mediaStream.getVideoTracks()[0], mediaStream)
        }
}

/**
 * Wrapper for MediaStreamTrack.
 **/
public abstract class JsMediaTrack(
    public val nativeTrack: MediaStreamTrack,
    public val nativeStream: MediaStream
) : WebRtcMedia.Track {
    public override val id: String = nativeTrack.id
    public override val kind: WebRtcMedia.TrackType = nativeTrack.kind.toTrackKind()

    public override val enabled: Boolean
        get() = nativeTrack.enabled

    override fun enable(enabled: Boolean) {
        nativeTrack.enabled = enabled
    }

    override fun <T> getNative(): T = nativeTrack as? T ?: error("T should be MediaStreamTrack")

    override fun close() {
        nativeTrack.stop()
    }

    public companion object {
        public fun from(
            nativeTrack: MediaStreamTrack,
            nativeStream: MediaStream
        ): JsMediaTrack = when (nativeTrack.kind.toTrackKind()) {
            WebRtcMedia.TrackType.AUDIO -> JsAudioTrack(nativeTrack, nativeStream)
            WebRtcMedia.TrackType.VIDEO -> JsVideoTrack(nativeTrack, nativeStream)
        }
    }
}

public class JsAudioTrack(nativeTrack: MediaStreamTrack, nativeStream: MediaStream) :
    WebRtcMedia.AudioTrack, JsMediaTrack(nativeTrack, nativeStream)

public class JsVideoTrack(nativeTrack: MediaStreamTrack, nativeStream: MediaStream) :
    WebRtcMedia.VideoTrack, JsMediaTrack(nativeTrack, nativeStream)
