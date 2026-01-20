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
            inner.track()?.let { RustMediaTrack.from(nativeTrack = it, coroutineScope = null) }
        }

    override suspend fun replaceTrack(withTrack: WebRtcMedia.Track?) {
        if (withTrack != null && withTrack !is RustMediaTrack) {
            error("Wrong track implementation. Expected RustMediaTrack.")
        }
        inner.setTrack(withTrack?.inner)
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

    // There is no degradation preference in WebRTC.rs
    override val degradationPreference: WebRtc.DegradationPreference = WebRtc.DegradationPreference.DISABLED
}

/**
 * Returns implementation of the rtp sender that is used under the hood. Use it with caution.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.rs.getNative)
 */
public fun WebRtc.RtpSender.getNative(): RtpSender {
    val sender = this as? RustRtpSender ?: error("Wrong Rtp sender implementation.")
    return sender.inner
}

/**
 * Returns implementation of the rtp parameters that is used under the hood. Use it with caution.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.rs.getNative)
 */
public fun WebRtc.RtpParameters.getNative(): RtpParameters {
    val parameters = this as? RustRtpParameters ?: error("Wrong parameters implementation.")
    return parameters.inner
}
