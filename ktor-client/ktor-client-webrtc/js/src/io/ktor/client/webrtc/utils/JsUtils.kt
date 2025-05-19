/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.*
import io.ktor.client.webrtc.peer.*
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

public fun RTCSessionDescription.toCommon(): WebRTC.SessionDescription = WebRTC.SessionDescription(
    sdp = sdp,
    type = type.toSdpDescriptionType(),
)

public fun WebRTC.SessionDescription.toJS(): RTCSessionDescription {
    // RTCSessionDescription constructor is deprecated.
    // All methods that accept RTCSessionDescription objects also accept objects with the same properties,
    // so you can use a plain object instead of creating an RTCSessionDescription instance.
    return jsObject {
        sdp = this@toJS.sdp
        type = this@toJS.type.toJs()
    }
}

public fun RTCIceCandidate.toCommon(): WebRTC.IceCandidate = WebRTC.IceCandidate(
    candidate = candidate,
    sdpMid = sdpMid!!,
    sdpMLineIndex = sdpMLineIndex?.toInt()!!
)

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
