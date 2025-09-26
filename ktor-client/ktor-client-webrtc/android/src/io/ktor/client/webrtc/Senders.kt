/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.media.AndroidMediaTrack
import org.webrtc.DtmfSender
import org.webrtc.RtpParameters
import org.webrtc.RtpSender

public class AndroidRtpSender(internal val nativeSender: RtpSender) : WebRtc.RtpSender {
    override val dtmf: WebRtc.DtmfSender?
        get() = nativeSender.dtmf()?.let { AndroidDtmfSender(it) }

    override val track: WebRtcMedia.Track?
        get() = nativeSender.track()?.let { AndroidMediaTrack.from(it) }

    override suspend fun replaceTrack(withTrack: WebRtcMedia.Track?) {
        if (withTrack == null) {
            if (!nativeSender.setTrack(null, true)) {
                error("Failed to replace track.")
            }
            return
        }
        val track = withTrack as? AndroidMediaTrack ?: error("Track should extend AndroidMediaTrack.")
        if (!nativeSender.setTrack(track.nativeTrack, false)) {
            error("Failed to replace track.")
        }
    }

    override suspend fun getParameters(): WebRtc.RtpParameters {
        return AndroidRtpParameters(nativeSender.parameters)
    }

    override suspend fun setParameters(parameters: WebRtc.RtpParameters) {
        val parameters = parameters as? AndroidRtpParameters ?: error("Parameters should extend AndroidRtpParameters.")
        nativeSender.parameters = parameters.nativeRtpParameters
    }
}

public class AndroidDtmfSender(internal val nativeSender: DtmfSender) : WebRtc.DtmfSender {
    override val toneBuffer: String
        get() = nativeSender.tones()

    override val canInsertDtmf: Boolean
        get() = nativeSender.canInsertDtmf()

    override fun insertDtmf(tones: String, duration: Int, interToneGap: Int) {
        nativeSender.insertDtmf(tones, duration, interToneGap)
    }
}

public class AndroidRtpParameters(internal val nativeRtpParameters: RtpParameters) : WebRtc.RtpParameters {
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
        get() = nativeRtpParameters.degradationPreference?.toKtor() ?: WebRtc.DegradationPreference.DISABLED
}

/**
 * Returns implementation of the rtp sender that is used under the hood. Use it with caution.
 */
public fun WebRtc.RtpSender.getNative(): RtpSender {
    val sender = this as? AndroidRtpSender ?: error("Wrong Rtp sender implementation.")
    return sender.nativeSender
}

/**
 * Returns implementation of the dtmf sender that is used under the hood. Use it with caution.
 */
public fun WebRtc.DtmfSender.getNative(): DtmfSender {
    val sender = this as? AndroidDtmfSender ?: error("Wrong Dtmf sender implementation.")
    return sender.nativeSender
}

/**
 * Returns implementation of the rtp parameters that is used under the hood. Use it with caution.
 */
public fun WebRtc.RtpParameters.getNative(): RtpParameters {
    val parameters = this as? AndroidRtpParameters ?: error("Wrong parameters implementation.")
    return parameters.nativeRtpParameters
}
