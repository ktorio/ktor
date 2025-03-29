/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client.utils

import io.ktor.webrtc.client.IceServer
import io.ktor.webrtc.client.WebRTCAudioSourceStats
import io.ktor.webrtc.client.WebRTCStatsReport
import io.ktor.webrtc.client.WebRTCVideoSourceStats
import io.ktor.webrtc.client.WebRtcPeerConnection
import io.ktor.webrtc.client.peer.RTCIceCandidate
import io.ktor.webrtc.client.peer.RTCMediaStats
import io.ktor.webrtc.client.peer.RTCSessionDescription
import io.ktor.webrtc.client.peer.RTCSessionDescriptionInit
import io.ktor.webrtc.client.peer.RTCStatsReport
import io.ktor.webrtc.client.peer.RTCAudioStats
import io.ktor.webrtc.client.peer.RTCIceServer
import io.ktor.webrtc.client.peer.RTCStats
import io.ktor.webrtc.client.peer.RTCVideoStats
import io.ktor.webrtc.client.peer.ReadonlyMap
import org.w3c.dom.mediacapture.MediaStreamConstraints
import kotlin.js.toInt

public fun <T : JsAny> makeEmptyObject(): T = js("({})")

public fun audioEnabledConstraints(): MediaStreamConstraints = js("({ audio: true })")

public fun videoEnabledConstraints(): MediaStreamConstraints = js("({ video: true })")

public fun dateNow(): Long = js("Date.now()")

public fun makeIceServerObject(server: IceServer): RTCIceServer {
    return makeEmptyObject<RTCIceServer>().apply {
        urls = server.urls.toJsString()
        username = server.username?.toJsString()
        credential = server.credential?.toJsString()
    }
}

internal fun <K : JsAny, V : JsAny> arrayFromMapValues(map: ReadonlyMap<K>): JsArray<V> =
    js("Array.from(map.values())")

internal fun mapIceServers(iceServers: List<IceServer>): JsArray<RTCIceServer> =
    iceServers.map { makeIceServerObject(it) }.toJsArray()

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
    return RTCSessionDescription().also {
        it.type = when (type) {
            WebRtcPeerConnection.SessionDescriptionType.OFFER -> "offer"
            WebRtcPeerConnection.SessionDescriptionType.ANSWER -> "answer"
            WebRtcPeerConnection.SessionDescriptionType.ROLLBACK -> "rollback"
            WebRtcPeerConnection.SessionDescriptionType.PROVISIONAL_ANSWER -> "pranswer"
        }
        it.sdp = sdp
    }
}

public fun WebRtcPeerConnection.IceCandidate.toJS(): RTCIceCandidate {
    return RTCIceCandidate().also {
        it.sdpMLineIndex = sdpMLineIndex?.toJsNumber()
        it.candidate = candidate
        it.sdpMid = sdpMid
    }
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
public fun RTCStatsReport.toCommon(): WebRTCStatsReport {
    val entries = arrayFromMapValues<JsString, RTCStats>(this)
    var audio: WebRTCAudioSourceStats? = null
    var video: WebRTCVideoSourceStats? = null

    for (i in 0 until entries.length) {
        val entry = entries[i]
        if (entry == null || entry.type.toString() != "media-source") {
            continue
        }
        val kind = (entry as RTCMediaStats).kind.toString()
        if (kind == "audio") {
            entry as RTCAudioStats
            audio = WebRTCAudioSourceStats(
                timestamp = entry.timestamp.toDouble().toLong(),
                trackId = entry.trackId.toString(),
                type = "media-source",
                id = entry.id.toString(),
                audioLevel = entry.audioLevel?.toDouble(),
                totalAudioEnergy = entry.totalAudioEnergy?.toDouble(),
                totalSamplesDuration = entry.totalSamplesDuration?.toDouble(),
            )
        } else if (kind == "video") {
            entry as RTCVideoStats
            video = WebRTCVideoSourceStats(
                timestamp = entry.timestamp.toDouble().toLong(),
                trackId = entry.trackId.toString(),
                type = "media-source",
                id = entry.id.toString(),
                width = entry.width?.toInt(),
                height = entry.height?.toInt(),
                frames = entry.frames?.toInt(),
                framesPerSecond = entry.framesPerSecond?.toInt()
            )
        } else {
            error("Unknown media source kind: $kind")
        }
    }
    return WebRTCStatsReport(
        timestamp = dateNow(),
        audio = audio,
        video = video,
    )
}
