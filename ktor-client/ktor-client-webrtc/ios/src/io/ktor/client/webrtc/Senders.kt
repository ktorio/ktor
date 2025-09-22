/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import WebRTC.RTCDegradationPreference
import WebRTC.RTCDtmfSenderProtocol
import WebRTC.RTCRtpCodecParameters
import WebRTC.RTCRtpEncodingParameters
import WebRTC.RTCRtpHeaderExtension
import WebRTC.RTCRtpParameters
import WebRTC.RTCRtpSender
import io.ktor.client.webrtc.media.IosMediaTrack
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
public class IosRtpSender(internal val nativeSender: RTCRtpSender) : WebRtc.RtpSender {
    override val dtmf: WebRtc.DtmfSender?
        get() = nativeSender.dtmfSender()?.let { IosDtmfSender(it) }

    override val track: WebRtcMedia.Track?
        get() = nativeSender.track()?.let { IosMediaTrack.from(it) }

    override suspend fun replaceTrack(withTrack: WebRtcMedia.Track?) {
        if (withTrack == null) {
            nativeSender.setTrack(null)
            return
        }
        nativeSender.setTrack((withTrack as IosMediaTrack).nativeTrack)
    }

    override suspend fun getParameters(): WebRtc.RtpParameters {
        return IosRtpParameters(nativeSender.parameters)
    }

    override suspend fun setParameters(parameters: WebRtc.RtpParameters) {
        nativeSender.parameters = (parameters as IosRtpParameters).nativeRtpParameters
    }
}

@OptIn(ExperimentalForeignApi::class)
public class IosDtmfSender(internal val nativeSender: RTCDtmfSenderProtocol) : WebRtc.DtmfSender {
    override val toneBuffer: String
        get() = nativeSender.remainingTones()

    override val canInsertDtmf: Boolean
        get() = nativeSender.canInsertDtmf()

    override fun insertDtmf(tones: String, duration: Int, interToneGap: Int) {
        nativeSender.insertDtmf(tones, duration.toDouble(), interToneGap.toDouble())
    }
}

@OptIn(ExperimentalForeignApi::class)
public class IosRtpParameters(internal val nativeRtpParameters: RTCRtpParameters) : WebRtc.RtpParameters {
    override val transactionId: String = nativeRtpParameters.transactionId
    override val rtcp: Any = nativeRtpParameters.rtcp

    @Suppress("UNCHECKED_CAST")
    override val encodings: List<RTCRtpEncodingParameters> =
        nativeRtpParameters.encodings as List<RTCRtpEncodingParameters>

    @Suppress("UNCHECKED_CAST")
    override val codecs: List<RTCRtpCodecParameters> = nativeRtpParameters.codecs as List<RTCRtpCodecParameters>

    override val headerExtensions: List<WebRtc.RtpHeaderExtensionParameters>
        get() = nativeRtpParameters.headerExtensions.map {
            val ext = it as RTCRtpHeaderExtension
            WebRtc.RtpHeaderExtensionParameters(ext.id, ext.uri, ext.encrypted)
        }

    override val degradationPreference: WebRtc.DegradationPreference
        get() = when (nativeRtpParameters.degradationPreference!!.longValue) {
            RTCDegradationPreference.RTCDegradationPreferenceBalanced.value -> WebRtc.DegradationPreference.BALANCED
            RTCDegradationPreference.RTCDegradationPreferenceDisabled.value -> WebRtc.DegradationPreference.DISABLED
            RTCDegradationPreference.RTCDegradationPreferenceMaintainFramerate.value -> WebRtc.DegradationPreference.MAINTAIN_FRAMERATE
            RTCDegradationPreference.RTCDegradationPreferenceMaintainResolution.value -> WebRtc.DegradationPreference.MAINTAIN_RESOLUTION
            else -> error("Unknown RTCDegradationPreference: ${nativeRtpParameters.degradationPreference}")
        }
}

/**
 * Returns implementation of the rtp sender that is used under the hood. Use it with caution.
 */
@OptIn(ExperimentalForeignApi::class)
public fun WebRtc.RtpSender.getNative(): RTCRtpSender {
    val sender = this as? IosRtpSender ?: error("Wrong Rtp sender implementation.")
    return sender.nativeSender
}

/**
 * Returns implementation of the dtmf sender that is used under the hood. Use it with caution.
 */
@OptIn(ExperimentalForeignApi::class)
public fun WebRtc.DtmfSender.getNative(): RTCDtmfSenderProtocol {
    val sender = this as? IosDtmfSender ?: error("Wrong Dtmf sender implementation.")
    return sender.nativeSender
}

/**
 * Returns implementation of the rtp parameters that is used under the hood. Use it with caution.
 */
@OptIn(ExperimentalForeignApi::class)
public fun WebRtc.RtpParameters.getNative(): RTCRtpParameters {
    val parameters = this as? IosRtpParameters ?: error("Wrong parameters implementation.")
    return parameters.nativeRtpParameters
}
