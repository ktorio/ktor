/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client.utils

import io.ktor.webrtc.client.*
import io.ktor.webrtc.client.engine.*
import io.ktor.webrtc.client.peer.*
import kotlinx.browser.document
import org.w3c.dom.mediacapture.MediaStreamTrack
import org.w3c.dom.mediacapture.MediaTrackConstraints
import kotlin.js.collections.JsMap
import kotlin.js.collections.toMap

public fun makeEmptyObject(): dynamic = js("({})")

public fun AudioTrackConstraints.toJS(): MediaTrackConstraints {
    return MediaTrackConstraints(
        volume = volume,
        latency = latency,
        sampleRate = sampleRate,
        sampleSize = sampleSize,
        echoCancellation = echoCancellation,
        autoGainControl = autoGainControl,
        noiseSuppression = noiseSuppression,
        channelCount = channelCount,
    )
}

public fun VideoTrackConstraints.toJS(): MediaTrackConstraints {
    return MediaTrackConstraints(
        width = width,
        height = height,
        aspectRatio = aspectRatio,
        facingMode = facingMode?.toJs(),
        frameRate = frameRate,
        resizeMode = resizeMode?.toJs(),
    )
}

public fun makeDummyAudioStreamTrack(): MediaStreamTrack {
    val ctx = js("new AudioContext()")
    val oscillator = ctx.createOscillator()
    val dst = oscillator.connect(ctx.createMediaStreamDestination())
    oscillator.start()
    return dst.stream.getAudioTracks()[0]
}

public fun makeDummyVideoStreamTrack(width: Int, height: Int): MediaStreamTrack {
    val canvas: dynamic = document.createElement("canvas")
    canvas.width = width
    canvas.height = height
    val ctx = canvas.getContext("2d")
    ctx.fillRect(0, 0, width, height)
    val stream = canvas.captureStream()
    return stream.getVideoTracks()[0]
}

public fun RTCSessionDescriptionInit.toCommon(): WebRtcPeerConnection.SessionDescription {
    return WebRtcPeerConnection.SessionDescription(
        type = when (type) {
            "offer" -> WebRtcPeerConnection.SessionDescriptionType.OFFER
            "answer" -> WebRtcPeerConnection.SessionDescriptionType.ANSWER
            "pranswer" -> WebRtcPeerConnection.SessionDescriptionType.PROVISIONAL_ANSWER
            "rollback" -> WebRtcPeerConnection.SessionDescriptionType.ROLLBACK
            else -> WebRtcPeerConnection.SessionDescriptionType.OFFER
        },
        sdp = sdp.toString()
    )
}

public fun WebRtcPeerConnection.SessionDescription.toJS(): RTCSessionDescription {
    // RTCSessionDescription constructor is deprecated.
    // All methods that accept RTCSessionDescription objects also accept objects with the same properties,
    // so you can use a plain object instead of creating an RTCSessionDescription instance.
    val options = makeEmptyObject()
    options.sdp = sdp
    options.type = when (type) {
        WebRtcPeerConnection.SessionDescriptionType.OFFER -> "offer"
        WebRtcPeerConnection.SessionDescriptionType.ANSWER -> "answer"
        WebRtcPeerConnection.SessionDescriptionType.ROLLBACK -> "rollback"
        WebRtcPeerConnection.SessionDescriptionType.PROVISIONAL_ANSWER -> "pranswer"
    }
    return options
}

public fun WebRtcPeerConnection.IceCandidate.toJS(): RTCIceCandidate {
    val options = makeEmptyObject()
    options.sdpMLineIndex = sdpMLineIndex
    options.candidate = candidate
    options.sdpMid = sdpMid
    return RTCIceCandidate(options)
}

private fun <T> getValues(map: dynamic): Array<T> = js("Array.from(map.values())")

@OptIn(ExperimentalJsCollectionsApi::class)
private fun objectToMap(obj: dynamic): JsMap<String, Any?> = js("new Map(Object.entries(obj))")

@OptIn(ExperimentalJsCollectionsApi::class)
@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
public fun RTCStatsReport.toCommon(): List<WebRTCStats> {
    return getValues<RTCStats>(this).map { entry ->
        WebRTCStats(
            timestamp = entry.timestamp.toLong(),
            type = entry.type,
            id = entry.id,
            props = objectToMap(entry).toMap()
        )
    }.toList()
}
