/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.webrtc

import io.ktor.client.webrtc.browser.*
import kotlinx.coroutines.await

/**
 * Wrapper for RTCRtpSender.
 */
public class WasmJsRtpSender(internal val nativeSender: RTCRtpSender) : WebRtc.RtpSender {
    override val dtmf: WebRtc.DtmfSender? get() = nativeSender.dtmf?.let { WasmJsDtmfSender(it) }

    override val track: WebRtcMedia.Track?
        get() = nativeSender.track?.let {
            WasmJsMediaTrack.from(it)
        }

    override suspend fun replaceTrack(withTrack: WebRtcMedia.Track?) {
        nativeSender.replaceTrack((withTrack as? WasmJsMediaTrack)?.nativeTrack)
    }

    override suspend fun getParameters(): WebRtc.RtpParameters {
        return WasmJsRtpParameters(nativeSender.getParameters()?.unsafeCast() ?: error("Failed to get parameters."))
    }

    override suspend fun setParameters(parameters: WebRtc.RtpParameters) {
        (parameters as? WasmJsRtpParameters)?.let {
            nativeSender.setParameters(it.nativeRtpParameters).await<WebRtc.RtpParameters>()
        }
    }
}

/**
 * Wrapper for RTCRtpSender.
 */
public class WasmJsDtmfSender(internal val nativeSender: RTCDTMFSender) : WebRtc.DtmfSender {
    override val toneBuffer: String
        get() = nativeSender.toneBuffer.toString()
    override val canInsertDtmf: Boolean
        get() = nativeSender.canInsertDTMF.toBoolean()

    override fun insertDtmf(tones: String, duration: Int, interToneGap: Int) {
        nativeSender.insertDTMF(tones.toJsString(), duration.toJsNumber(), interToneGap.toJsNumber())
    }
}

/**
 * Wrapper for RTCRtpSendParameters.
 */
public class WasmJsRtpParameters(internal val nativeRtpParameters: RTCRtpSendParameters) : WebRtc.RtpParameters {
    override val transactionId: String = nativeRtpParameters.transactionId.toString()
    override val encodings: Iterable<RTCRtpEncodingParameters> = nativeRtpParameters.encodings.toArray().asIterable()
    override val codecs: Iterable<RTCRtpCodecParameters> = nativeRtpParameters.codecs.toArray().asIterable()
    override val rtcp: RTCRtcpParameters = nativeRtpParameters.rtcp

    override val headerExtensions: List<WebRtc.RtpHeaderExtensionParameters>
        get() = nativeRtpParameters.headerExtensions.toArray().map {
            WebRtc.RtpHeaderExtensionParameters(
                it.id.toInt(),
                it.uri.toString(),
                it.encrypted ?: false
            )
        }

    override val degradationPreference: WebRtc.DegradationPreference
        get() = nativeRtpParameters.degradationPreference.toString().toDegradationPreference()
}

/**
 * Returns implementation of the rtp sender that is used under the hood. Use it with caution.
 */
public fun WebRtc.RtpSender.getNative(): RTCRtpSender {
    val sender = this as? WasmJsRtpSender ?: error("Wrong sender implementation.")
    return sender.nativeSender
}

/**
 * Returns implementation of the dtmf sender that is used under the hood. Use it with caution.
 */
public fun WebRtc.DtmfSender.getNative(): RTCDTMFSender {
    val sender = this as? WasmJsDtmfSender ?: error("Wrong sender implementation.")
    return sender.nativeSender
}

/**
 * Returns implementation of the rtp parameters that is used under the hood. Use it with caution.
 */
public fun WebRtc.RtpParameters.getNative(): RTCRtpSendParameters {
    val parameters = this as? WasmJsRtpParameters ?: error("Wrong parameters implementation.")
    return parameters.nativeRtpParameters
}
