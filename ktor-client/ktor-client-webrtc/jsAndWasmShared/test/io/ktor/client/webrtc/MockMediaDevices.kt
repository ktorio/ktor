package io.ktor.client.webrtc

object MockMediaTrackFactory : MediaTrackFactory {
    private var allowVideo = false
    private var allowAudio = false

    fun grantPermissions(audio: Boolean, video: Boolean) {
        allowAudio = audio
        allowVideo = video
    }

    override suspend fun createAudioTrack(constraints: WebRTCMedia.AudioTrackConstraints): WebRTCMedia.AudioTrack {
        if (!allowAudio) throw WebRTCMedia.PermissionException("audio")
        return makeDummyAudioStreamTrack()
    }

    override suspend fun createVideoTrack(constraints: WebRTCMedia.VideoTrackConstraints): WebRTCMedia.VideoTrack {
        if (!allowVideo) throw WebRTCMedia.PermissionException("video")
        return makeDummyVideoStreamTrack(constraints.width ?: 100, constraints.height ?: 100)
    }
}

// Record silence as an audio track
expect fun makeDummyAudioStreamTrack(): WebRTCMedia.AudioTrack

// Capture canvas as a video track
expect fun makeDummyVideoStreamTrack(width: Int, height: Int): WebRTCMedia.VideoTrack
