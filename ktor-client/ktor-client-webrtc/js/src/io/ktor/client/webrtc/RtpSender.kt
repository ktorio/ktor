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

public class JsRtpSender(public val nativeSender: RTCRtpSender) : WebRtc.RtpSender {
    override val dtmf: WebRtc.DtmfSender? get() = nativeSender.dtmf?.let { JsDtmfSender(it) }

    override val track: WebRtcMedia.Track?
        get() = nativeSender.track?.let {
            JsMediaTrack.from(
                it,
                MediaStream()
            )
        }

    override fun <T> getNative(): T = nativeSender as? T ?: error("T should be RTCRtpSender")

    override suspend fun replaceTrack(withTrack: WebRtcMedia.Track?) {
        nativeSender.replaceTrack((withTrack as? JsMediaTrack)?.nativeTrack)
    }

    override suspend fun getParameters(): WebRtc.RtpParameters {
        return JsRtpParameters(nativeSender.getParameters().unsafeCast<RTCRtpSendParameters>())
    }

    override suspend fun setParameters(parameters: WebRtc.RtpParameters) {
        (parameters as? JsRtpParameters)?.let { nativeSender.setParameters(it.nativeRtpParameters).await() }
    }
}

public class JsDtmfSender(private val nativeSender: RTCDTMFSender) : WebRtc.DtmfSender {
    override val toneBuffer: String
        get() = nativeSender.toneBuffer
    override val canInsertDTMF: Boolean
        get() = nativeSender.canInsertDTMF

    override fun <T> getNative(): T = nativeSender as? T ?: error("T should be RTCDTMFSender")

    override fun insertDTMF(tones: String, duration: Int, interToneGap: Int) {
        nativeSender.insertDTMF(tones, duration, interToneGap)
    }
}

public class JsRtpParameters(public val nativeRtpParameters: RTCRtpSendParameters) : WebRtc.RtpParameters {
    override val transactionId: String = nativeRtpParameters.transactionId
    override val encodings: Iterable<RTCRtpEncodingParameters> = nativeRtpParameters.encodings.asIterable()
    override val codecs: Iterable<RTCRtpCodecParameters> = nativeRtpParameters.codecs.asIterable()
    override val rtcp: RTCRtcpParameters = nativeRtpParameters.rtcp

    override val headerExtensions: List<WebRtc.RtpHeaderExtensionParameters>
        get() = nativeRtpParameters.headerExtensions.map {
            WebRtc.RtpHeaderExtensionParameters(
                it.id.toInt(),
                it.uri,
                it.encrypted ?: false
            )
        }

    override val degradationPreference: WebRtc.DegradationPreference
        get() = nativeRtpParameters.degradationPreference.toDegradationPreference()
}
