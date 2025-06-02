/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.webrtc

import io.ktor.client.webrtc.browser.RTCDTMFSender
import io.ktor.client.webrtc.browser.RTCRtcpParameters
import io.ktor.client.webrtc.browser.RTCRtpCodecParameters
import io.ktor.client.webrtc.browser.RTCRtpEncodingParameters
import io.ktor.client.webrtc.browser.RTCRtpSendParameters
import io.ktor.client.webrtc.browser.RTCRtpSender
import kotlinx.coroutines.await
import org.w3c.dom.mediacapture.MediaStream

public class JsRtpSender(public val nativeSender: RTCRtpSender) : WebRTC.RtpSender {
    override val dtmf: WebRTC.DtmfSender? get() = nativeSender.dtmf?.let { JsDtmfSender(it) }

    override val track: WebRTCMedia.Track? get() = nativeSender.track?.let { JsMediaTrack.from(it, MediaStream()) }

    override fun getNative(): Any = nativeSender

    override suspend fun replaceTrack(withTrack: WebRTCMedia.Track?) {
        nativeSender.replaceTrack((withTrack as? JsMediaTrack)?.nativeTrack)
    }

    override suspend fun getParameters(): WebRTC.RtpParameters {
        return JsRtpParameters(nativeSender.getParameters().unsafeCast<RTCRtpSendParameters>())
    }

    override suspend fun setParameters(parameters: WebRTC.RtpParameters) {
        (parameters as? JsRtpParameters)?.let { nativeSender.setParameters(it.nativeRtpParameters).await() }
    }
}

public class JsDtmfSender(private val nativeSender: RTCDTMFSender) : WebRTC.DtmfSender {
    override val toneBuffer: String
        get() = nativeSender.toneBuffer
    override val canInsertDTMF: Boolean
        get() = nativeSender.canInsertDTMF

    override fun getNative(): Any = nativeSender

    override fun insertDTMF(tones: String, duration: Int, interToneGap: Int) {
        nativeSender.insertDTMF(tones, duration, interToneGap)
    }
}

public class JsRtpParameters(public val nativeRtpParameters: RTCRtpSendParameters) : WebRTC.RtpParameters {
    override val transactionId: String = nativeRtpParameters.transactionId
    override val encodings: Iterable<RTCRtpEncodingParameters> = nativeRtpParameters.encodings.asIterable()
    override val codecs: Iterable<RTCRtpCodecParameters> = nativeRtpParameters.codecs.asIterable()
    override val rtcp: RTCRtcpParameters = nativeRtpParameters.rtcp

    override val headerExtensions: List<WebRTC.RtpHeaderExtensionParameters>
        get() = nativeRtpParameters.headerExtensions.map {
            WebRTC.RtpHeaderExtensionParameters(
                it.id.toInt(),
                it.uri,
                it.encrypted ?: false
            )
        }

    override val degradationPreference: WebRTC.DegradationPreference
        get() = nativeRtpParameters.degradationPreference.toDegradationPreference()
}
