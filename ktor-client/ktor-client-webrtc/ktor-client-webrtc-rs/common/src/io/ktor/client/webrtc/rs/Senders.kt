/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.rs

import io.ktor.client.webrtc.*
import kotlinx.coroutines.runBlocking
import uniffi.ktor_client_webrtc.*

public class RustRtpSender(internal val inner: RtpSender) : WebRtc.RtpSender {
    override val dtmf: WebRtc.DtmfSender?
        get() = null // dtmf is not supported by WebRTC.rs

    override val track: WebRtcMedia.Track?
        get() = runBlocking {
            inner.track()?.let { RustMediaTrack.from(nativeTrack = it) }
        }

    override suspend fun replaceTrack(withTrack: WebRtcMedia.Track?) {
        inner.setTrack((withTrack as? RustMediaTrack)?.inner)
    }

    override suspend fun getParameters(): WebRtc.RtpParameters {
        return RustRtpParameters(inner.getParameters())
    }

    override suspend fun setParameters(parameters: WebRtc.RtpParameters) {
        throw UnsupportedOperationException("Setting parameters is not supported by WebRTC.rs")
    }
}

public class RustRtpParameters(internal val inner: RtpParameters) : WebRtc.RtpParameters {
    override val transactionId: String = ""
    override val encodings: List<RtpEncodingParameters> = inner.encodings
    override val codecs: List<RtpCodecParameters> = inner.codecs
    override val rtcp: Any = "Unsupported parameter"

    override val headerExtensions: List<WebRtc.RtpHeaderExtensionParameters>
        get() = inner.headerExtensions.map {
            WebRtc.RtpHeaderExtensionParameters(
                it.id.toInt(),
                it.uri,
                it.encrypted
            )
        }

    override val degradationPreference: WebRtc.DegradationPreference
        get() = when (inner.degradationPreference) {
            DegradationPreference.BALANCED -> WebRtc.DegradationPreference.BALANCED
            DegradationPreference.MAINTAIN_FRAMERATE -> WebRtc.DegradationPreference.MAINTAIN_FRAMERATE
            DegradationPreference.MAINTAIN_RESOLUTION -> WebRtc.DegradationPreference.MAINTAIN_RESOLUTION
            DegradationPreference.DISABLED -> WebRtc.DegradationPreference.DISABLED
        }
}

/**
 * Returns implementation of the rtp sender that is used under the hood. Use it with caution.
 */
public fun WebRtc.RtpSender.getNative(): RtpSender {
    val sender = this as? RustRtpSender ?: error("Wrong Rtp sender implementation.")
    return sender.inner
}

/**
 * Returns implementation of the rtp parameters that is used under the hood. Use it with caution.
 */
public fun WebRtc.RtpParameters.getNative(): RtpParameters {
    val parameters = this as? RustRtpParameters ?: error("Wrong parameters implementation.")
    return parameters.inner
}
