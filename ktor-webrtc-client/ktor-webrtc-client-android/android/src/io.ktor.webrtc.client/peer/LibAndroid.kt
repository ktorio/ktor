/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client.peer

import io.ktor.webrtc.client.*
import org.webrtc.DtmfSender
import org.webrtc.MediaStreamTrack
import org.webrtc.RtpParameters
import org.webrtc.RtpSender

public abstract class AndroidMediaTrack(
    public val nativeTrack: MediaStreamTrack
) : WebRTCMedia.Track {
    public override val id: String = nativeTrack.id()

    public override val kind: WebRTCMedia.TrackType = kindOf(nativeTrack)

    public override val enabled: Boolean
        get() = nativeTrack.enabled()

    override fun enable(enabled: Boolean) {
        nativeTrack.setEnabled(enabled)
    }

    override fun close(): Unit = nativeTrack.dispose()

    public companion object {
        private fun kindOf(nativeTrack: MediaStreamTrack): WebRTCMedia.TrackType {
            return when (nativeTrack.kind()) {
                "audio" -> WebRTCMedia.TrackType.AUDIO
                "video" -> WebRTCMedia.TrackType.VIDEO
                else -> error("Unknown media track kind: ${nativeTrack.kind()}")
            }
        }

        public fun from(nativeTrack: MediaStreamTrack): AndroidMediaTrack = when (kindOf(nativeTrack)) {
            WebRTCMedia.TrackType.AUDIO -> AndroidAudioTrack(nativeTrack)
            WebRTCMedia.TrackType.VIDEO -> AndroidVideoTrack(nativeTrack)
        }
    }
}

public class AndroidAudioTrack(nativeTrack: MediaStreamTrack) : WebRTCMedia.AudioTrack, AndroidMediaTrack(nativeTrack)

public class AndroidVideoTrack(nativeTrack: MediaStreamTrack) : WebRTCMedia.VideoTrack, AndroidMediaTrack(nativeTrack)


public class AndroidRtpSender(public val nativeRtpSender: RtpSender) : WebRTC.RtpSender {
    override val dtmf: WebRTC.DtmfSender?
        get() = nativeRtpSender.dtmf()?.let { AndroidDtmfServer(it) }

    override val track: WebRTCMedia.Track?
        get() = nativeRtpSender.track()?.let { AndroidMediaTrack.from(it) }

    override suspend fun replaceTrack(withTrack: WebRTCMedia.Track?) {
        nativeRtpSender.setTrack((withTrack as? AndroidMediaTrack)?.nativeTrack, false)
    }

    override suspend fun getParameters(): WebRTC.RtpParameters {
        return AndroidRtpParameters(nativeRtpSender.parameters)
    }

    override suspend fun setParameters(parameters: WebRTC.RtpParameters) {
        nativeRtpSender.setParameters((parameters as? AndroidRtpParameters)?.nativeRtpParameters)
    }
}

public class AndroidDtmfServer(private val nativeRtpSender: DtmfSender) : WebRTC.DtmfSender {
    override val toneBuffer: String
        get() = nativeRtpSender.tones()

    override val canInsertDTMF: Boolean
        get() = nativeRtpSender.canInsertDtmf()

    override fun insertDTMF(tones: String, duration: Int, interToneGap: Int) {
        nativeRtpSender.insertDtmf(tones, duration, interToneGap)
    }
}

public class AndroidRtpParameters(public val nativeRtpParameters: RtpParameters) : WebRTC.RtpParameters {
    override val transactionId: String = nativeRtpParameters.transactionId
    override val encodings: List<RtpParameters.Encoding> = nativeRtpParameters.encodings
    override val codecs: List<RtpParameters.Codec> = nativeRtpParameters.codecs
    override val rtcp: Any = nativeRtpParameters.rtcp

    override val headerExtensions: List<WebRTC.RtpHeaderExtensionParameters>
        get() = nativeRtpParameters.headerExtensions.map {
            WebRTC.RtpHeaderExtensionParameters(
                it.id,
                it.uri,
                it.encrypted
            )
        }

    override val degradationPreference: WebRTC.DegradationPreference
        get() = when (nativeRtpParameters.degradationPreference) {
            RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION -> WebRTC.DegradationPreference.MAINTAIN_RESOLUTION
            RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE -> WebRTC.DegradationPreference.MAINTAIN_FRAMERATE
            RtpParameters.DegradationPreference.BALANCED -> WebRTC.DegradationPreference.BALANCED
            else -> WebRTC.DegradationPreference.DISABLED
        }
}
