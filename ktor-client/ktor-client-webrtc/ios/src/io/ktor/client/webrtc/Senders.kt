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

/**
 * iOS implementation of RTP sender that wraps native RTCRtpSender.
 *
 * Provides platform-safe access to RTP sender functionality including track management,
 * DTMF capabilities, and parameter configuration.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.IosRtpSender)
 */
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

/**
 * iOS implementation of DTMF sender that wraps native RTCDtmfSenderProtocol.
 *
 * Provides platform-safe API for sending DTMF tones with proper time unit conversion
 * from milliseconds to seconds for the native iOS implementation.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.IosDtmfSender)
 */
@OptIn(ExperimentalForeignApi::class)
public class IosDtmfSender(internal val nativeSender: RTCDtmfSenderProtocol) : WebRtc.DtmfSender {
    override val toneBuffer: String
        get() = nativeSender.remainingTones()

    override val canInsertDtmf: Boolean
        get() = nativeSender.canInsertDtmf()

    override fun insertDtmf(tones: String, duration: Int, interToneGap: Int) {
        // map milliseconds to seconds
        nativeSender.insertDtmf(tones, duration / 1000.0, interToneGap / 1000.0)
    }
}

/**
 * iOS implementation of RTP parameters that wraps native RTCRtpParameters.
 *
 * Provides type-safe access to RTP configuration, including encodings, codecs,
 * header extensions, and degradation preferences with proper enum mapping.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.IosRtpParameters)
 */
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
        get() = when (nativeRtpParameters.degradationPreference?.longValue) {
            RTCDegradationPreference.RTCDegradationPreferenceBalanced.value -> WebRtc.DegradationPreference.BALANCED
            RTCDegradationPreference.RTCDegradationPreferenceDisabled.value -> WebRtc.DegradationPreference.DISABLED
            RTCDegradationPreference.RTCDegradationPreferenceMaintainFramerate.value ->
                WebRtc.DegradationPreference.MAINTAIN_FRAMERATE
            RTCDegradationPreference.RTCDegradationPreferenceMaintainResolution.value ->
                WebRtc.DegradationPreference.MAINTAIN_RESOLUTION
            else -> WebRtc.DegradationPreference.BALANCED
        }
}

/**
 * Returns implementation of the rtp sender that is used under the hood. Use it with caution.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.getNative)
 */
@OptIn(ExperimentalForeignApi::class)
public fun WebRtc.RtpSender.getNative(): RTCRtpSender {
    return (this as IosRtpSender).nativeSender
}

/**
 * Returns implementation of the dtmf sender that is used under the hood. Use it with caution.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.getNative)
 */
@OptIn(ExperimentalForeignApi::class)
public fun WebRtc.DtmfSender.getNative(): RTCDtmfSenderProtocol {
    return (this as IosDtmfSender).nativeSender
}

/**
 * Returns implementation of the rtp parameters that is used under the hood. Use it with caution.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.getNative)
 */
@OptIn(ExperimentalForeignApi::class)
public fun WebRtc.RtpParameters.getNative(): RTCRtpParameters {
    return (this as IosRtpParameters).nativeRtpParameters
}
