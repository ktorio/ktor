/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.utils

import io.ktor.client.webrtc.*
import io.ktor.client.webrtc.browser.*
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.dom.mediacapture.MediaTrackConstraints
import org.w3c.files.Blob
import kotlin.js.Promise

public fun emptyObject(): JsAny = js("({})")

/**
 * Creates a JavaScript object with the given type and initializes it.
 */
internal inline fun <T : JsAny> jsObject(init: T.() -> Unit): T {
    val obj = emptyObject().unsafeCast<T>()
    init(obj)
    return obj
}

internal fun WebRtcMedia.AudioTrackConstraints.toJs(): MediaTrackConstraints {
    return jsObject {
        volume = this@toJs.volume?.toJsNumber()
        latency = this@toJs.latency?.toJsNumber()
        sampleRate = this@toJs.sampleRate?.toJsNumber()
        sampleSize = this@toJs.sampleSize?.toJsNumber()
        echoCancellation = this@toJs.echoCancellation?.toJsBoolean()
        autoGainControl = this@toJs.autoGainControl?.toJsBoolean()
        noiseSuppression = this@toJs.noiseSuppression?.toJsBoolean()
        channelCount = this@toJs.channelCount?.toJsNumber()
    }
}

internal fun WebRtcMedia.VideoTrackConstraints.toJs(): MediaTrackConstraints {
    return jsObject {
        width = this@toJs.width?.toJsNumber()
        height = this@toJs.height?.toJsNumber()
        aspectRatio = this@toJs.aspectRatio?.toJsNumber()
        facingMode = this@toJs.facingMode?.toJs()?.toJsString()
        frameRate = this@toJs.frameRate?.toJsNumber()
        resizeMode = this@toJs.resizeMode?.toJs()?.toJsString()
    }
}

internal fun WebRtc.IceServer.toJs(): RTCIceServer = jsObject {
    urls = this@toJs.urls.toJsString()
    username = this@toJs.username?.toJsString()
    credential = this@toJs.credential?.toJsString()
}

internal fun RTCSessionDescription.toKtor(): WebRtc.SessionDescription {
    return WebRtc.SessionDescription(
        sdp = sdp.toString(),
        type = type.toString().toSdpDescriptionType(),
    )
}

internal fun WebRtc.SessionDescription.toJs(): RTCSessionDescription {
    // RTCSessionDescription constructor is deprecated.
    // All methods that accept RTCSessionDescription objects also accept objects with the same properties,
    // so you can use a plain object instead of creating an RTCSessionDescription instance.
    return jsObject {
        sdp = this@toJs.sdp.toJsString()
        type = this@toJs.type.toJs().toJsString()
    }
}

internal fun RTCIceCandidate.toKtor(): WebRtc.IceCandidate = WebRtc.IceCandidate(
    candidate = candidate.toString(),
    sdpMid = sdpMid.toString(),
    sdpMLineIndex = sdpMLineIndex.toInt()
)

public fun WebRtc.IceCandidate.toJs(): RTCIceCandidate {
    val options = jsObject<RTCIceCandidateInit> {
        sdpMLineIndex = this@toJs.sdpMLineIndex.toJsNumber()
        candidate = this@toJs.candidate.toJsString()
        sdpMid = this@toJs.sdpMid.toJsString()
    }
    return RTCIceCandidate(options)
}

@Suppress("UNUSED_VARIABLE")
private fun <T : JsAny> getValues(map: JsAny): JsArray<T> = js("Array.from(map.values())")

@Suppress("UNUSED_VARIABLE")
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
public fun RTCStatsReport.toKtor(): List<WebRtc.Stats> {
    return getValues<RTCStats>(this).toArray().map { stats ->
        WebRtc.Stats(
            timestamp = stats.timestamp.toDouble().toLong(),
            type = stats.type.toString(),
            id = stats.id.toString(),
            props = kotlinMapFromEntries(stats)
        )
    }.toList()
}

internal fun WebRtcDataChannelOptions.toJs() = jsObject<RTCDataChannelInit> {
    id = this@toJs.id?.toJsNumber()
    ordered = this@toJs.ordered
    protocol = this@toJs.protocol.toJsString()
    negotiated = this@toJs.negotiated
    if (this@toJs.maxRetransmits != null) { // null is also considered as a value, should leave undefined
        maxRetransmits = this@toJs.maxRetransmits?.toJsNumber()
    }
    if (this@toJs.maxPacketLifeTime != null) { // null is also considered as a value, should leave undefined
        maxPacketLifeTime = this@toJs.maxPacketLifeTime?.inWholeMilliseconds?.toInt()?.toJsNumber()
    }
}

internal fun ByteArray.toJs(): Uint8Array {
    val buffer = Uint8Array(size)
    for (i in 0 until size) {
        buffer[i] = this[i]
    }
    return buffer
}

internal fun ArrayBuffer.toKotlin(): ByteArray {
    val view = Uint8Array(this)
    val buffer = ByteArray(view.byteLength)
    for (i in 0 until view.byteLength) {
        buffer[i] = view[i]
    }
    return buffer
}

// Should be removed when `kotlin-wrappers` are introduced
@Suppress("WRONG_EXTERNAL_DECLARATION")
internal external fun Blob.asArrayBuffer(): Promise<ArrayBuffer>
