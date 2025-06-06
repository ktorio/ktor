/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.browser.*
import io.ktor.client.webrtc.utils.*
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
            val streamConstrains = MediaStreamConstraints(audio = constraints.toJS())
            val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await<MediaStream>()
            return WasmJsAudioTrack(mediaStream.getAudioTracks()[0]!!, mediaStream)
        }

    override suspend fun createVideoTrack(constraints: WebRtcMedia.VideoTrackConstraints): WebRtcMedia.VideoTrack =
        withPermissionException("video") {
            val streamConstrains = MediaStreamConstraints(audio = constraints.toJS())
            val mediaStream = navigator.mediaDevices.getUserMedia(streamConstrains).await<MediaStream>()
            return WasmJsVideoTrack(mediaStream.getVideoTracks()[0]!!, mediaStream)
        }
}

/**
 * Wrapper for MediaStreamTrack.
 **/
public abstract class WasmJsMediaTrack(
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
        ): WasmJsMediaTrack = when (nativeTrack.kind.toTrackKind()) {
            WebRtcMedia.TrackType.AUDIO -> WasmJsAudioTrack(nativeTrack, nativeStream)
            WebRtcMedia.TrackType.VIDEO -> WasmJsVideoTrack(nativeTrack, nativeStream)
        }
    }
}

public class WasmJsAudioTrack(nativeTrack: MediaStreamTrack, nativeStream: MediaStream) :
    WebRtcMedia.AudioTrack, WasmJsMediaTrack(nativeTrack, nativeStream)

public class WasmJsVideoTrack(nativeTrack: MediaStreamTrack, nativeStream: MediaStream) :
    WebRtcMedia.VideoTrack, WasmJsMediaTrack(nativeTrack, nativeStream)
