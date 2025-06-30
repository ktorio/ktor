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
internal inline fun <T : Any> jsObject(init: T.() -> Unit): T {
    val obj = js("{}").unsafeCast<T>()
    init(obj)
    return obj
}

internal fun WebRtcMedia.AudioTrackConstraints.toJs(): MediaTrackConstraints {
    return jsObject {
        volume = this@toJs.volume
        latency = this@toJs.latency
        sampleRate = this@toJs.sampleRate
        sampleSize = this@toJs.sampleSize
        echoCancellation = this@toJs.echoCancellation
        autoGainControl = this@toJs.autoGainControl
        noiseSuppression = this@toJs.noiseSuppression
        channelCount = this@toJs.channelCount
    }
}

internal fun WebRtcMedia.VideoTrackConstraints.toJs(): MediaTrackConstraints {
    return jsObject {
        width = this@toJs.width
        height = this@toJs.height
        aspectRatio = this@toJs.aspectRatio
        facingMode = this@toJs.facingMode?.toJs()
        frameRate = this@toJs.frameRate
        resizeMode = this@toJs.resizeMode?.toJs()
    }
}

/**
 * Converts a browser RTCSessionDescription to the common WebRtc.SessionDescription model.
 */
internal fun RTCSessionDescription.toKtor(): WebRtc.SessionDescription = WebRtc.SessionDescription(
    sdp = sdp,
    type = type.toSdpDescriptionType(),
)

internal fun WebRtc.SessionDescription.toJs(): RTCSessionDescription {
    // RTCSessionDescription constructor is deprecated.
    // All methods that accept RTCSessionDescription objects also accept objects with the same properties,
    // so you can use a plain object instead of creating an RTCSessionDescription instance.
    return jsObject {
        sdp = this@toJs.sdp
        type = this@toJs.type.toJs()
    }
}

internal fun RTCIceCandidate.toKtor(): WebRtc.IceCandidate = WebRtc.IceCandidate(
    candidate = candidate,
    sdpMid = sdpMid!!,
    sdpMLineIndex = sdpMLineIndex?.toInt()!!
)

internal fun WebRtc.IceCandidate.toJs(): RTCIceCandidate {
    val options = jsObject<RTCIceCandidateInit> {
        sdpMLineIndex = this@toJs.sdpMLineIndex
        candidate = this@toJs.candidate
        sdpMid = this@toJs.sdpMid
    }
    return RTCIceCandidate(options)
}

internal fun WebRtc.IceServer.toJs(): RTCIceServer = jsObject {
    urls = this@toJs.urls
    username = this@toJs.username
    credential = this@toJs.credential
}

/**
 * Extracts values from a JavaScript map-like object into an array.
 */
@Suppress("UNUSED_PARAMETER")
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
internal fun RTCStatsReport.toKtor(): List<WebRtc.Stats> {
    return getValues<RTCStats>(this).map { entry ->
        WebRtc.Stats(
            timestamp = entry.timestamp.toLong(),
            type = entry.type,
            id = entry.id,
            props = objectToMap(entry).toMap()
        )
    }.toList()
}
