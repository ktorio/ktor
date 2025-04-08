/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client.utils

import io.ktor.webrtc.client.*
import io.ktor.webrtc.client.engine.*
import io.ktor.webrtc.client.peer.*
import org.w3c.dom.mediacapture.MediaStreamTrack
import org.w3c.dom.mediacapture.MediaTrackConstraints

public fun <T : JsAny> makeEmptyObject(): T = js("({})")

public fun AudioTrackConstraints.toJS(): MediaTrackConstraints {
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

public fun VideoTrackConstraints.toJS(): MediaTrackConstraints {
    return MediaTrackConstraints(
        width = width?.toJsNumber(),
        height = height?.toJsNumber(),
        aspectRatio = aspectRatio?.toJsNumber(),
        facingMode = facingMode?.toJs()?.toJsString(),
        frameRate = frameRate?.toJsNumber(),
        resizeMode = resizeMode?.toJs()?.toJsString(),
    )
}

public fun makeIceServerObject(server: IceServer): RTCIceServer {
    return makeEmptyObject<RTCIceServer>().apply {
        urls = server.urls.toJsString()
        username = server.username?.toJsString()
        credential = server.credential?.toJsString()
    }
}

public fun makeDummyAudioStreamTrack(): MediaStreamTrack = js(
    """{
        const ctx = new AudioContext();
        const oscillator = ctx.createOscillator();
        const dst = oscillator.connect(ctx.createMediaStreamDestination());
        oscillator.start();
        return dst.stream.getAudioTracks()[0];
    }"""
)

public fun makeDummyVideoStreamTrack(width: Int, height: Int): MediaStreamTrack = js(
    """{
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext("2d");
        ctx.fillRect(0, 0, width, height);
        const stream = canvas.captureStream();
        return stream.getVideoTracks()[0];
    }"""
)

internal fun mapIceServers(iceServers: List<IceServer>): JsArray<RTCIceServer> =
    iceServers.map { makeIceServerObject(it) }.toJsArray()

public fun RTCSessionDescriptionInit.toCommon(): WebRtcPeerConnection.SessionDescription {
    return WebRtcPeerConnection.SessionDescription(
        type = when (type.toString()) {
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
    return makeEmptyObject<RTCSessionDescription>().also {
        it.type = when (type) {
            WebRtcPeerConnection.SessionDescriptionType.OFFER -> "offer".toJsString()
            WebRtcPeerConnection.SessionDescriptionType.ANSWER -> "answer".toJsString()
            WebRtcPeerConnection.SessionDescriptionType.ROLLBACK -> "rollback".toJsString()
            WebRtcPeerConnection.SessionDescriptionType.PROVISIONAL_ANSWER -> "pranswer".toJsString()
        }
        it.sdp = sdp.toJsString()
    }
}

public fun WebRtcPeerConnection.IceCandidate.toJS(): RTCIceCandidate {
    val options = makeEmptyObject<RTCIceCandidateInit>()
    options.sdpMLineIndex = sdpMLineIndex?.toJsNumber()
    options.candidate = candidate.toJsString()
    options.sdpMid = sdpMid?.toJsString()
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

public fun RTCStatsReport.toCommon(): List<WebRTCStats> {
    return getValues<RTCStats>(this).toArray().map { stats ->
        WebRTCStats(
            timestamp = stats.timestamp.toDouble().toLong(),
            type = stats.type.toString(),
            id = stats.id.toString(),
            props = kotlinMapFromEntries(stats)
        )
    }.toList()
}
