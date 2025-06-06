/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.*
import io.ktor.client.webrtc.browser.RTCIceCandidate
import io.ktor.client.webrtc.browser.RTCIceCandidateInit
import io.ktor.client.webrtc.browser.RTCIceServer
import io.ktor.client.webrtc.browser.RTCSessionDescription
import io.ktor.client.webrtc.browser.RTCStats
import io.ktor.client.webrtc.browser.RTCStatsReport
import org.w3c.dom.mediacapture.MediaTrackConstraints

public fun emptyObject(): JsAny = js("({})")

/**
 * Creates a JavaScript object with the given type and initializes it.
 */
public inline fun <T : JsAny> jsObject(init: T.() -> Unit): T {
    val obj = emptyObject().unsafeCast<T>()
    init(obj)
    return obj
}

public fun WebRtcMedia.AudioTrackConstraints.toJS(): MediaTrackConstraints {
    return MediaTrackConstraints(
        volume = volume?.toJsNumber(),
        latency = latency?.toJsNumber(),
        sampleRate = sampleRate?.toJsNumber(),
        sampleSize = sampleSize?.toJsNumber(),
        echoCancellation = echoCancellation?.toJsBoolean(),
        autoGainControl = autoGainControl?.toJsBoolean(),
        noiseSuppression = noiseSuppression?.toJsBoolean(),
        channelCount = channelCount?.toJsNumber(),
    )
}

public fun WebRtcMedia.VideoTrackConstraints.toJS(): MediaTrackConstraints {
    return MediaTrackConstraints(
        width = width?.toJsNumber(),
        height = height?.toJsNumber(),
        aspectRatio = aspectRatio?.toJsNumber(),
        facingMode = facingMode?.toJs()?.toJsString(),
        frameRate = frameRate?.toJsNumber(),
        resizeMode = resizeMode?.toJs()?.toJsString(),
    )
}

public fun makeIceServerObject(server: WebRtc.IceServer): RTCIceServer = jsObject {
    urls = server.urls.toJsString()
    username = server.username?.toJsString()
    credential = server.credential?.toJsString()
}

internal fun mapIceServers(iceServers: List<WebRtc.IceServer>): JsArray<RTCIceServer> =
    iceServers.map { makeIceServerObject(it) }.toJsArray()

public fun RTCSessionDescription.toCommon(): WebRtc.SessionDescription {
    return WebRtc.SessionDescription(
        sdp = sdp.toString(),
        type = type.toString().toSdpDescriptionType(),
    )
}

public fun WebRtc.SessionDescription.toJS(): RTCSessionDescription {
    // RTCSessionDescription constructor is deprecated.
    // All methods that accept RTCSessionDescription objects also accept objects with the same properties,
    // so you can use a plain object instead of creating an RTCSessionDescription instance.
    return jsObject {
        sdp = this@toJS.sdp.toJsString()
        type = this@toJS.type.toJs().toJsString()
    }
}

public fun RTCIceCandidate.toCommon(): WebRtc.IceCandidate = WebRtc.IceCandidate(
    candidate = candidate.toString(),
    sdpMid = sdpMid.toString(),
    sdpMLineIndex = sdpMLineIndex.toInt()
)

public fun WebRtc.IceCandidate.toJS(): RTCIceCandidate {
    val options = jsObject<RTCIceCandidateInit> {
        sdpMLineIndex = this@toJS.sdpMLineIndex.toJsNumber()
        candidate = this@toJS.candidate.toJsString()
        sdpMid = this@toJS.sdpMid.toJsString()
    }
    return RTCIceCandidate(options)
}

private fun <T : JsAny> getValues(map: JsAny): JsArray<T> = js("Array.from(map.values())")

private fun entries(obj: JsAny): JsArray<JsArray<JsAny>> = js("Object.entries(obj)")

private fun deserializeJsItem(item: JsAny?): Any? {
    return when (item) {
        is JsString -> item.toString()
        is JsNumber -> item.toInt()
        is JsBoolean -> item.toBoolean()
        else -> null // maybe throw an error?
    }
}

private fun kotlinMapFromEntries(obj: JsAny): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    for (entry in entries(obj).toArray()) {
        val key = entry[0].toString()
        val value = deserializeJsItem(entry[1])
        if (value != null) {
            map[key] = value
        }
    }
    return map
}

/**
 * Converts a browser RTCStatsReport to a list of common WebRtc.Stats objects.
 * Extracts values from the report map and converts each entry to the common format.
 */
public fun RTCStatsReport.toCommon(): List<WebRtc.Stats> {
    return getValues<RTCStats>(this).toArray().map { stats ->
        WebRtc.Stats(
            timestamp = stats.timestamp.toDouble().toLong(),
            type = stats.type.toString(),
            id = stats.id.toString(),
            props = kotlinMapFromEntries(stats)
        )
    }.toList()
}
