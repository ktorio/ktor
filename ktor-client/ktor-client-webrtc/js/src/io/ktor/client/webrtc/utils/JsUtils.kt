/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.*
import io.ktor.client.webrtc.browser.*
import org.w3c.dom.mediacapture.MediaTrackConstraints
import kotlin.js.collections.JsMap
import kotlin.js.collections.toMap

/**
 * Creates a JavaScript object with the given type and initializes it.
 */
public inline fun <T : Any> jsObject(init: T.() -> Unit): T {
    val obj = js("{}").unsafeCast<T>()
    init(obj)
    return obj
}

public fun WebRtcMedia.AudioTrackConstraints.toJS(): MediaTrackConstraints {
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

public fun WebRtcMedia.VideoTrackConstraints.toJS(): MediaTrackConstraints {
    return MediaTrackConstraints(
        width = width,
        height = height,
        aspectRatio = aspectRatio,
        facingMode = facingMode?.toJs(),
        frameRate = frameRate,
        resizeMode = resizeMode?.toJs(),
    )
}

/**
 * Converts a browser RTCSessionDescription to the common WebRtc.SessionDescription model.
 */
public fun RTCSessionDescription.toCommon(): WebRtc.SessionDescription = WebRtc.SessionDescription(
    sdp = sdp,
    type = type.toSdpDescriptionType(),
)

public fun WebRtc.SessionDescription.toJS(): RTCSessionDescription {
    // RTCSessionDescription constructor is deprecated.
    // All methods that accept RTCSessionDescription objects also accept objects with the same properties,
    // so you can use a plain object instead of creating an RTCSessionDescription instance.
    return jsObject {
        sdp = this@toJS.sdp
        type = this@toJS.type.toJs()
    }
}

public fun RTCIceCandidate.toCommon(): WebRtc.IceCandidate = WebRtc.IceCandidate(
    candidate = candidate,
    sdpMid = sdpMid!!,
    sdpMLineIndex = sdpMLineIndex?.toInt()!!
)

public fun WebRtc.IceCandidate.toJS(): RTCIceCandidate {
    val options = jsObject<RTCIceCandidateInit> {
        sdpMLineIndex = this@toJS.sdpMLineIndex
        candidate = this@toJS.candidate
        sdpMid = this@toJS.sdpMid
    }
    return RTCIceCandidate(options)
}

/**
 * Extracts values from a JavaScript map-like object into an array.
 */
private fun <T> getValues(map: dynamic): Array<T> = js("Array.from(map.values())")

/**
 * Converts a JavaScript object to a JsMap with string keys.
 */
@OptIn(ExperimentalJsCollectionsApi::class)
private fun objectToMap(obj: dynamic): JsMap<String, Any?> = js("new Map(Object.entries(obj))")

/**
 * Converts a browser RTCStatsReport to a list of common WebRtc.Stats objects.
 * Extracts values from the report map and converts each entry to the common format.
 */
@OptIn(ExperimentalJsCollectionsApi::class)
public fun RTCStatsReport.toCommon(): List<WebRtc.Stats> {
    return getValues<RTCStats>(this).map { entry ->
        WebRtc.Stats(
            timestamp = entry.timestamp.toLong(),
            type = entry.type,
            id = entry.id,
            props = objectToMap(entry).toMap()
        )
    }.toList()
}
