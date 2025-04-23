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

public inline fun <T : Any> jsObject(init: T.() -> Unit): T {
    val obj = js("{}").unsafeCast<T>()
    init(obj)
    return obj
}

public fun WebRTCMedia.AudioTrackConstraints.toJS(): MediaTrackConstraints {
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

public fun WebRTCMedia.VideoTrackConstraints.toJS(): MediaTrackConstraints {
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
    return dst.stream.getAudioTracks()[0] as MediaStreamTrack
}

public fun makeDummyVideoStreamTrack(width: Int, height: Int): MediaStreamTrack {
    val canvas: dynamic = document.createElement("canvas")
    canvas.width = width
    canvas.height = height
    val ctx = canvas.getContext("2d")
    ctx.fillRect(0, 0, width, height)
    val stream = canvas.captureStream()
    return stream.getVideoTracks()[0] as MediaStreamTrack
}

public fun RTCSessionDescriptionInit.toCommon(): WebRTC.SessionDescription {
    return WebRTC.SessionDescription(
        type = when (type) {
            "offer" -> WebRTC.SessionDescriptionType.OFFER
            "answer" -> WebRTC.SessionDescriptionType.ANSWER
            "pranswer" -> WebRTC.SessionDescriptionType.PROVISIONAL_ANSWER
            "rollback" -> WebRTC.SessionDescriptionType.ROLLBACK
            else -> WebRTC.SessionDescriptionType.OFFER
        },
        sdp = sdp.toString()
    )
}

public fun WebRTC.SessionDescription.toJS(): RTCSessionDescription {
    // RTCSessionDescription constructor is deprecated.
    // All methods that accept RTCSessionDescription objects also accept objects with the same properties,
    // so you can use a plain object instead of creating an RTCSessionDescription instance.
    val common = this
    return jsObject<RTCSessionDescription> {
        sdp = common.sdp
        type = when (common.type) {
            WebRTC.SessionDescriptionType.OFFER -> "offer"
            WebRTC.SessionDescriptionType.ANSWER -> "answer"
            WebRTC.SessionDescriptionType.ROLLBACK -> "rollback"
            WebRTC.SessionDescriptionType.PROVISIONAL_ANSWER -> "pranswer"
        }
    }
}

public fun WebRTC.IceCandidate.toJS(): RTCIceCandidate {
    val options = jsObject<RTCIceCandidateInit> {
        sdpMLineIndex = this@toJS.sdpMLineIndex
        candidate = this@toJS.candidate
        sdpMid = this@toJS.sdpMid
    }
    return RTCIceCandidate(options)
}

private fun <T> getValues(map: dynamic): Array<T> = js("Array.from(map.values())")

@OptIn(ExperimentalJsCollectionsApi::class)
private fun objectToMap(obj: dynamic): JsMap<String, Any?> = js("new Map(Object.entries(obj))")

@OptIn(ExperimentalJsCollectionsApi::class)
public fun RTCStatsReport.toCommon(): List<WebRTC.Stats> {
    return getValues<RTCStats>(this).map { entry ->
        WebRTC.Stats(
            timestamp = entry.timestamp.toLong(),
            type = entry.type,
            id = entry.id,
            props = objectToMap(entry).toMap()
        )
    }.toList()
}
