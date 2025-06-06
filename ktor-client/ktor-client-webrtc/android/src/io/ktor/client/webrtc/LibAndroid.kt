/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import org.webrtc.DtmfSender
import org.webrtc.MediaStreamTrack
import org.webrtc.RtpParameters
import org.webrtc.RtpSender

/**
 * Wrapper for org.webrtc.MediaStreamTrack.
 **/
public abstract class AndroidMediaTrack(
    public val nativeTrack: MediaStreamTrack,
    private val onDispose: (() -> Unit)?
) : WebRtcMedia.Track {
    public override val id: String = nativeTrack.id()

    public override val kind: WebRtcMedia.TrackType = kindOf(nativeTrack)

    public override val enabled: Boolean
        get() = nativeTrack.enabled()

    override fun enable(enabled: Boolean) {
        nativeTrack.setEnabled(enabled)
    }

    override fun <T> getNative(): T = nativeTrack as? T ?: error("T should be org.webrtc.MediaStreamTrack")

    override fun close() {
        onDispose?.invoke()
        nativeTrack.dispose()
    }

    public companion object {
        private fun kindOf(nativeTrack: MediaStreamTrack): WebRtcMedia.TrackType {
            return when (nativeTrack.kind()) {
                "audio" -> WebRtcMedia.TrackType.AUDIO
                "video" -> WebRtcMedia.TrackType.VIDEO
                else -> error("Unknown media track kind: ${nativeTrack.kind()}")
            }
        }

        public fun from(nativeTrack: MediaStreamTrack): AndroidMediaTrack = when (kindOf(nativeTrack)) {
            WebRtcMedia.TrackType.AUDIO -> AndroidAudioTrack(nativeTrack)
            WebRtcMedia.TrackType.VIDEO -> AndroidVideoTrack(nativeTrack)
        }
    }
}

public class AndroidAudioTrack(nativeTrack: MediaStreamTrack, onDispose: (() -> Unit)? = null) : WebRtcMedia.AudioTrack,
    AndroidMediaTrack(nativeTrack, onDispose)

public class AndroidVideoTrack(nativeTrack: MediaStreamTrack, onDispose: (() -> Unit)? = null) : WebRtcMedia.VideoTrack,
    AndroidMediaTrack(nativeTrack, onDispose)

public class AndroidRtpSender(public val nativeRtpSender: RtpSender) : WebRtc.RtpSender {
    override val dtmf: WebRtc.DtmfSender?
        get() = nativeRtpSender.dtmf()?.let { AndroidDtmfServer(it) }

    override val track: WebRtcMedia.Track?
        get() = nativeRtpSender.track()?.let { AndroidMediaTrack.from(it) }

    override fun <T> getNative(): T = nativeRtpSender as? T ?: error("T should be org.webrtc.RtpSender")

    override suspend fun replaceTrack(withTrack: WebRtcMedia.Track?) {
        nativeRtpSender.setTrack((withTrack as? AndroidMediaTrack)?.nativeTrack, false)
    }

    override suspend fun getParameters(): WebRtc.RtpParameters {
        return AndroidRtpParameters(nativeRtpSender.parameters)
    }

    override suspend fun setParameters(parameters: WebRtc.RtpParameters) {
        nativeRtpSender.parameters = (parameters as? AndroidRtpParameters)?.nativeRtpParameters
    }
}

public class AndroidDtmfServer(private val nativeRtpSender: DtmfSender) : WebRtc.DtmfSender {
    override val toneBuffer: String
        get() = nativeRtpSender.tones()

    override val canInsertDTMF: Boolean
        get() = nativeRtpSender.canInsertDtmf()

    override fun <T> getNative(): T = nativeRtpSender as? T ?: error("T should be org.webrtc.DtmfSender")

    override fun insertDTMF(tones: String, duration: Int, interToneGap: Int) {
        nativeRtpSender.insertDtmf(tones, duration, interToneGap)
    }
}

public class AndroidRtpParameters(public val nativeRtpParameters: RtpParameters) : WebRtc.RtpParameters {
    override val transactionId: String = nativeRtpParameters.transactionId
    override val encodings: List<RtpParameters.Encoding> = nativeRtpParameters.encodings
    override val codecs: List<RtpParameters.Codec> = nativeRtpParameters.codecs
    override val rtcp: Any = nativeRtpParameters.rtcp

    override val headerExtensions: List<WebRtc.RtpHeaderExtensionParameters>
        get() = nativeRtpParameters.headerExtensions.map {
            WebRtc.RtpHeaderExtensionParameters(
                it.id,
                it.uri,
                it.encrypted
            )
        }

    override val degradationPreference: WebRtc.DegradationPreference
        get() = when (nativeRtpParameters.degradationPreference) {
            RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION -> WebRtc.DegradationPreference.MAINTAIN_RESOLUTION
            RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE -> WebRtc.DegradationPreference.MAINTAIN_FRAMERATE
            RtpParameters.DegradationPreference.BALANCED -> WebRtc.DegradationPreference.BALANCED
            else -> WebRtc.DegradationPreference.DISABLED
        }
}
