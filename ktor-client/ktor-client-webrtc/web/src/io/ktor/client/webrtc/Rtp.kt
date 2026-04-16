/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import web.rtc.*
import kotlin.js.toArray

/**
 * Wrapper for RTCRtpSender.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.JsRtpSender)
 */
public class JsRtpSender(internal val nativeSender: RTCRtpSender) : WebRtc.RtpSender {
    override val dtmf: WebRtc.DtmfSender? get() = nativeSender.dtmf?.let { JsDtmfSender(it) }

    override val track: WebRtcMedia.Track?
        get() = nativeSender.track?.let {
            JsMediaTrack.from(it)
        }

    override suspend fun replaceTrack(withTrack: WebRtcMedia.Track?) {
        if (withTrack == null) {
            nativeSender.replaceTrack(null)
            return
        }
        nativeSender.replaceTrack((withTrack as JsMediaTrack).nativeTrack)
    }

    override suspend fun getParameters(): WebRtc.RtpParameters {
        return JsRtpParameters(nativeSender.getParameters())
    }

    override suspend fun setParameters(parameters: WebRtc.RtpParameters) {
        val params = parameters as JsRtpParameters
        nativeSender.setParameters(params.nativeRtpParameters)
    }
}

/**
 * Wrapper for RTCRtpSender.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.JsDtmfSender)
 */
public class JsDtmfSender(internal val nativeSender: RTCDTMFSender) : WebRtc.DtmfSender {
    override val toneBuffer: String
        get() = nativeSender.toneBuffer
    override val canInsertDtmf: Boolean
        get() = nativeSender.canInsertDTMF

    override fun insertDtmf(tones: String, duration: Int, interToneGap: Int) {
        nativeSender.insertDTMF(tones, duration, interToneGap)
    }
}

/**
 * Wrapper for RTCRtpSendParameters.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.JsRtpParameters)
 */
public class JsRtpParameters(internal val nativeRtpParameters: RTCRtpSendParameters) : WebRtc.RtpParameters {
    override val transactionId: String = nativeRtpParameters.transactionId
    override val encodings: Iterable<RTCRtpEncodingParameters> = nativeRtpParameters.encodings.toArray().asIterable()
    override val codecs: Iterable<RTCRtpCodecParameters> = nativeRtpParameters.codecs.toArray().asIterable()
    override val rtcp: RTCRtcpParameters = nativeRtpParameters.rtcp

    override val headerExtensions: List<WebRtc.RtpHeaderExtensionParameters>
        get() = nativeRtpParameters.headerExtensions.toArray().map {
            WebRtc.RtpHeaderExtensionParameters(
                it.id.toInt(),
                it.uri,
                it.encrypted ?: false
            )
        }

    override val degradationPreference: WebRtc.DegradationPreference
        get() = nativeRtpParameters.degradationPreference.toKtor()
}

/**
 * Returns implementation of the rtp sender that is used under the hood. Use it with caution.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.getNative)
 */
public fun WebRtc.RtpSender.getNative(): RTCRtpSender {
    return (this as JsRtpSender).nativeSender
}

/**
 * Returns implementation of the dtmf sender that is used under the hood. Use it with caution.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.getNative)
 */
public fun WebRtc.DtmfSender.getNative(): RTCDTMFSender {
    return (this as JsDtmfSender).nativeSender
}

/**
 * Returns implementation of the rtp parameters that is used under the hood. Use it with caution.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.getNative)
 */
public fun WebRtc.RtpParameters.getNative(): RTCRtpSendParameters {
    return (this as JsRtpParameters).nativeRtpParameters
}
