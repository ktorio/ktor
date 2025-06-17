/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.webrtc

import io.ktor.client.webrtc.media.*
import web.rtc.RTCDTMFSender
import web.rtc.RTCRtcpParameters
import web.rtc.RTCRtpCodecParameters
import web.rtc.RTCRtpEncodingParameters
import web.rtc.RTCRtpSendParameters
import web.rtc.RTCRtpSender

public class JsRtpSender(internal val nativeSender: RTCRtpSender) : WebRtc.RtpSender {
    override val dtmf: WebRtc.DtmfSender? get() = nativeSender.dtmf?.let { JsDtmfSender(it) }

    override val track: WebRtcMedia.Track?
        get() = nativeSender.track?.let { JsMediaTrack.from(it) }

    override suspend fun replaceTrack(withTrack: WebRtcMedia.Track?) {
        if (track == null) {
            nativeSender.replaceTrack(null)
            return
        }
        val track = (withTrack as? JsMediaTrack) ?: error("JsMediaTrack is only supported")
        nativeSender.replaceTrack(track.nativeTrack)
    }

    override suspend fun getParameters(): WebRtc.RtpParameters {
        return JsRtpParameters(nativeSender.getParameters())
    }

    override suspend fun setParameters(parameters: WebRtc.RtpParameters) {
        val params = (parameters as? JsRtpParameters) ?: error("JsRtpParameters are only supported")
        nativeSender.setParameters(params.nativeRtpParameters)
    }
}

public class JsDtmfSender(internal val nativeSender: RTCDTMFSender) : WebRtc.DtmfSender {
    override val toneBuffer: String
        get() = nativeSender.toneBuffer
    override val canInsertDTMF: Boolean
        get() = nativeSender.canInsertDTMF

    override fun insertDTMF(tones: String, duration: Int, interToneGap: Int) {
        nativeSender.insertDTMF(tones, duration, interToneGap)
    }
}

public class JsRtpParameters(internal val nativeRtpParameters: RTCRtpSendParameters) : WebRtc.RtpParameters {
    override val transactionId: String = nativeRtpParameters.transactionId
    override val encodings: Iterable<RTCRtpEncodingParameters> = nativeRtpParameters.encodings.toArray().asIterable()
    override val codecs: Iterable<RTCRtpCodecParameters> = nativeRtpParameters.codecs.toArray().asIterable()
    override val rtcp: RTCRtcpParameters = nativeRtpParameters.rtcp

    override val headerExtensions: List<WebRtc.RtpHeaderExtensionParameters>
        get() = nativeRtpParameters.headerExtensions.toArray().map {
            WebRtc.RtpHeaderExtensionParameters(it.id.toInt(), it.uri, it.encrypted ?: false)
        }

    override val degradationPreference: WebRtc.DegradationPreference
        get() = nativeRtpParameters.degradationPreference.toKtor()
}

public fun WebRtc.RtpSender.getNative(): RTCRtpSender {
    val sender = this as? JsRtpSender ?: error("Only JsRtpSender implementation is supported.")
    return sender.nativeSender
}

public fun WebRtc.DtmfSender.getNative(): RTCDTMFSender {
    val sender = this as? JsDtmfSender ?: error("Only JsDtmfSender implementation is supported.")
    return sender.nativeSender
}

public fun WebRtc.RtpParameters.getNative(): RTCRtpSendParameters {
    val params = this as? JsRtpParameters ?: error("Only JsRtpParameters implementation is supported.")
    return params.nativeRtpParameters
}
