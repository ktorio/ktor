/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.*
import io.ktor.client.webrtc.peer.RTCIceCandidate
import io.ktor.client.webrtc.peer.RTCIceCandidateInit
import io.ktor.client.webrtc.peer.RTCIceServer
import io.ktor.client.webrtc.peer.RTCSessionDescription
import io.ktor.client.webrtc.peer.RTCSessionDescriptionInit
import io.ktor.client.webrtc.peer.RTCStats
import io.ktor.client.webrtc.peer.RTCStatsReport
import org.w3c.dom.mediacapture.MediaTrackConstraints

// any js block should be located in a separate function
public fun emptyObject(): JsAny = js("({})")

public inline fun <T : JsAny> jsObject(init: T.() -> Unit): T {
    val obj = emptyObject().unsafeCast<T>()
    init(obj)
    return obj
}

public fun WebRTCMedia.AudioTrackConstraints.toJS(): MediaTrackConstraints {
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

public fun WebRTCMedia.VideoTrackConstraints.toJS(): MediaTrackConstraints {
    return MediaTrackConstraints(
        width = width?.toJsNumber(),
        height = height?.toJsNumber(),
        aspectRatio = aspectRatio?.toJsNumber(),
        facingMode = facingMode?.toJs()?.toJsString(),
        frameRate = frameRate?.toJsNumber(),
        resizeMode = resizeMode?.toJs()?.toJsString(),
    )
}

public fun makeIceServerObject(server: WebRTC.IceServer): RTCIceServer = jsObject {
    urls = server.urls.toJsString()
    username = server.username?.toJsString()
    credential = server.credential?.toJsString()
}

internal fun mapIceServers(iceServers: List<WebRTC.IceServer>): JsArray<RTCIceServer> =
    iceServers.map { makeIceServerObject(it) }.toJsArray()

public fun RTCSessionDescriptionInit.toCommon(): WebRTC.SessionDescription {
    return WebRTC.SessionDescription(
        type = when (type.toString()) {
            "offer" -> WebRTC.SessionDescriptionType.OFFER
            "answer" -> WebRTC.SessionDescriptionType.ANSWER
            "rollback" -> WebRTC.SessionDescriptionType.ROLLBACK
            "pranswer" -> WebRTC.SessionDescriptionType.PROVISIONAL_ANSWER
            else -> WebRTC.SessionDescriptionType.OFFER
        },
        sdp = sdp.toString()
    )
}

public fun WebRTC.SessionDescription.toJS(): RTCSessionDescription {
    // RTCSessionDescription constructor is deprecated.
    // All methods that accept RTCSessionDescription objects also accept objects with the same properties,
    // so you can use a plain object instead of creating an RTCSessionDescription instance.
    return jsObject {
        type = when (this@toJS.type) {
            WebRTC.SessionDescriptionType.OFFER -> "offer".toJsString()
            WebRTC.SessionDescriptionType.ANSWER -> "answer".toJsString()
            WebRTC.SessionDescriptionType.ROLLBACK -> "rollback".toJsString()
            WebRTC.SessionDescriptionType.PROVISIONAL_ANSWER -> "pranswer".toJsString()
        }
        sdp = this@toJS.sdp.toJsString()
    }
}

public fun RTCIceCandidate.toCommon(): WebRTC.IceCandidate = WebRTC.IceCandidate(
    candidate = candidate.toString(),
    sdpMid = sdpMid.toString(),
    sdpMLineIndex = sdpMLineIndex.toInt()
)

public fun WebRTC.IceCandidate.toJS(): RTCIceCandidate {
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
        else -> null
    }
}

private fun kotlinMapFromEntries(obj: JsAny): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    for (entry in entries(obj).toArray()) {
        val key = entry[0].toString()
        val value = deserializeJsItem(entry[1]) // maybe throw error?
        if (value != null) {
            map[key] = value
        }
    }
    return map
}

public fun RTCStatsReport.toCommon(): List<WebRTC.Stats> {
    return getValues<RTCStats>(this).toArray().map { stats ->
        WebRTC.Stats(
            timestamp = stats.timestamp.toDouble().toLong(),
            type = stats.type.toString(),
            id = stats.id.toString(),
            props = kotlinMapFromEntries(stats)
        )
    }.toList()
}
