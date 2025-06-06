/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.client.webrtc.browser.JsUndefined
import io.ktor.client.webrtc.browser.RTCDTMFSender
import io.ktor.client.webrtc.browser.RTCRtcpParameters
import io.ktor.client.webrtc.browser.RTCRtpCodecParameters
import io.ktor.client.webrtc.browser.RTCRtpEncodingParameters
import io.ktor.client.webrtc.browser.RTCRtpSendParameters
import io.ktor.client.webrtc.browser.RTCRtpSender
import kotlinx.coroutines.await
import org.w3c.dom.mediacapture.MediaStream

public class WasmJsRtpSender(public val nativeSender: RTCRtpSender) : WebRtc.RtpSender {
    override val dtmf: WebRtc.DtmfSender? get() = nativeSender.dtmf?.let { WasmJsDtmfSender(it) }

    override val track: WebRtcMedia.Track?
        get() = nativeSender.track?.let { WasmJsMediaTrack.from(it, MediaStream()) }

    override fun <T> getNative(): T = nativeSender as? T ?: error("T should be RTCRtpSender")

    override suspend fun replaceTrack(withTrack: WebRtcMedia.Track?) {
        nativeSender.replaceTrack((withTrack as? WasmJsMediaTrack)?.nativeTrack)
    }

    override suspend fun getParameters(): WebRtc.RtpParameters {
        val params = nativeSender.getParameters()?.unsafeCast<RTCRtpSendParameters>() ?: error("Params are undefined")
        return WasmJsRtpParameters(params)
    }

    override suspend fun setParameters(parameters: WebRtc.RtpParameters) {
        if (parameters is WasmJsRtpParameters) {
            nativeSender.setParameters(parameters.nativeRtpParameters).await<JsUndefined>()
        }
    }
}

public class WasmJsDtmfSender(private val nativeSender: RTCDTMFSender) : WebRtc.DtmfSender {
    override val toneBuffer: String
        get() = nativeSender.toneBuffer.toString()
    override val canInsertDTMF: Boolean
        get() = nativeSender.canInsertDTMF.toBoolean()

    override fun <T> getNative(): T = nativeSender as? T ?: error("T should be RTCDTMFSender")

    override fun insertDTMF(tones: String, duration: Int, interToneGap: Int) {
        nativeSender.insertDTMF(tones.toJsString(), duration.toJsNumber(), interToneGap.toJsNumber())
    }
}

public class WasmJsRtpParameters(public val nativeRtpParameters: RTCRtpSendParameters) : WebRtc.RtpParameters {
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
        get() = nativeRtpParameters.degradationPreference?.toString().toDegradationPreference()
}

public actual object JsWebRtc : WebRtcClientEngineFactory<JsWebRtcEngineConfig> {
    actual override fun create(block: JsWebRtcEngineConfig.() -> Unit): WebRtcEngine =
        WasmJsWebRtcEngine(JsWebRtcEngineConfig().apply(block))
}
