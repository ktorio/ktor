/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.webrtc

import web.rtc.RTCDTMFSender
import web.rtc.RTCRtcpParameters
import web.rtc.RTCRtpCodecParameters
import web.rtc.RTCRtpEncodingParameters
import web.rtc.RTCRtpSendParameters
import web.rtc.RTCRtpSender
import web.rtc.replaceTrack
import web.rtc.setParameters

/**
 * Wrapper for RTCRtpSender.
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
        val track = withTrack as? JsMediaTrack ?: error("Track should extend JsMediaTrack.")
        nativeSender.replaceTrack(track.nativeTrack)
    }

    override suspend fun getParameters(): WebRtc.RtpParameters {
        return JsRtpParameters(nativeSender.getParameters())
    }

    override suspend fun setParameters(parameters: WebRtc.RtpParameters) {
        val params = parameters as? JsRtpParameters ?: error("Parameters should extend JsRtpParameters.")
        nativeSender.setParameters(params.nativeRtpParameters)
    }
}

/**
 * Wrapper for RTCRtpSender.
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
 */
public fun WebRtc.RtpSender.getNative(): RTCRtpSender {
    val sender = this as? JsRtpSender ?: error("Wrong sender implementation.")
    return sender.nativeSender
}

/**
 * Returns implementation of the dtmf sender that is used under the hood. Use it with caution.
 */
public fun WebRtc.DtmfSender.getNative(): RTCDTMFSender {
    val sender = this as? JsDtmfSender ?: error("Wrong sender implementation.")
    return sender.nativeSender
}

/**
 * Returns implementation of the rtp parameters that is used under the hood. Use it with caution.
 */
public fun WebRtc.RtpParameters.getNative(): RTCRtpSendParameters {
    val parameters = this as? JsRtpParameters ?: error("Wrong parameters implementation.")
    return parameters.nativeRtpParameters
}
