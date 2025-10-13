/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import dev.onvoid.webrtc.*
import io.ktor.client.webrtc.media.*

/**
 * JVM implementation of RTP sender that wraps native RTCRtpSender.
 *
 * Provides platform-safe access to RTP sender functionality including track management,
 * DTMF capabilities, and parameter configuration.
 */
public class JvmRtpSender(internal val inner: RTCRtpSender) : WebRtc.RtpSender {
    override val dtmf: WebRtc.DtmfSender?
        get() = inner.dtmfSender?.let { JvmDtmfSender(it) }

    override val track: WebRtcMedia.Track?
        get() = inner.track?.let { JvmMediaTrack.from(it) }

    override suspend fun replaceTrack(withTrack: WebRtcMedia.Track?) {
        if (withTrack == null) {
            return inner.replaceTrack(null)
        }
        val withTrack = withTrack as JvmMediaTrack
        inner.replaceTrack(withTrack.inner)
    }

    override suspend fun getParameters(): WebRtc.RtpParameters {
        return JvmRtpParameters(inner.parameters)
    }

    override suspend fun setParameters(parameters: WebRtc.RtpParameters) {
        inner.parameters = (parameters as JvmRtpParameters).inner
    }
}

/**
 * JVM implementation of DTMF sender that wraps native RTCDtmfSenderProtocol.
 *
 * Provides platform-safe API for sending DTMF tones with proper time unit conversion
 * from milliseconds to seconds for the native iOS implementation.
 */
public class JvmDtmfSender(internal val inner: RTCDtmfSender) : WebRtc.DtmfSender {
    override val toneBuffer: String
        get() = inner.tones()

    override val canInsertDtmf: Boolean
        get() = inner.canInsertDtmf()

    override fun insertDtmf(tones: String, duration: Int, interToneGap: Int) {
        inner.insertDtmf(tones, duration, interToneGap)
    }
}

/**
 * JVM implementation of RTP parameters that wraps native RTCRtpParameters.
 *
 * Provides type-safe access to RTP configuration, including encodings, codecs,
 * header extensions, and degradation preferences with proper enum mapping.
 */
public class JvmRtpParameters(internal val inner: RTCRtpSendParameters) : WebRtc.RtpParameters {
    override val transactionId: String = inner.transactionId
    override val encodings: List<RTCRtpEncodingParameters> = inner.encodings
    override val codecs: List<RTCRtpCodecParameters> = inner.codecs
    override val rtcp: RTCRtcpParameters = inner.rtcp

    override val headerExtensions: List<WebRtc.RtpHeaderExtensionParameters>
        get() = inner.headerExtensions.map {
            WebRtc.RtpHeaderExtensionParameters(it.id, it.uri, it.encrypted)
        }

    override val degradationPreference: WebRtc.DegradationPreference
        get() = WebRtc.DegradationPreference.DISABLED
}

/**
 * Returns implementation of the rtp sender that is used under the hood. Use it with caution.
 */
public fun WebRtc.RtpSender.getNative(): RTCRtpSender {
    return (this as JvmRtpSender).inner
}

/**
 * Returns implementation of the dtmf sender that is used under the hood. Use it with caution.
 */
public fun WebRtc.DtmfSender.getNative(): RTCDtmfSender {
    return (this as JvmDtmfSender).inner
}

/**
 * Returns implementation of the rtp parameters that is used under the hood. Use it with caution.
 */
public fun WebRtc.RtpParameters.getNative(): RTCRtpParameters {
    return (this as JvmRtpParameters).inner
}
