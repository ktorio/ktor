package io.ktor.client.webrtc

object MockMediaTrackFactory : MediaTrackFactory {
    private var allowVideo = false
    private var allowAudio = false

    fun grantPermissions(audio: Boolean, video: Boolean) {
        allowAudio = audio
        allowVideo = video
    }

    override suspend fun createAudioTrack(constraints: WebRtcMedia.AudioTrackConstraints): WebRtcMedia.AudioTrack {
        if (!allowAudio) throw WebRtcMedia.PermissionException("audio")
        return makeDummyAudioStreamTrack()
    }

    override suspend fun createVideoTrack(constraints: WebRtcMedia.VideoTrackConstraints): WebRtcMedia.VideoTrack {
        if (!allowVideo) throw WebRtcMedia.PermissionException("video")
        return makeDummyVideoStreamTrack(constraints.width ?: 100, constraints.height ?: 100)
    }
}

// Record silence as an audio track
expect fun makeDummyAudioStreamTrack(): WebRtcMedia.AudioTrack

// Capture canvas as a video track
expect fun makeDummyVideoStreamTrack(width: Int, height: Int): WebRtcMedia.VideoTrack
