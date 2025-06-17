/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("NON_EXTERNAL_TYPE_EXTENDS_EXTERNAL_TYPE")
package io.ktor.client.webrtc

import js.core.JsAny
import js.core.JsPrimitives.toDouble
import js.core.JsPrimitives.toJsBoolean
import js.core.JsPrimitives.toJsDouble
import js.core.JsPrimitives.toJsInt
import js.core.JsPrimitives.toJsString
import js.core.JsString
import js.objects.Object
import web.mediastreams.MediaTrackConstraints
import web.rtc.RTCBundlePolicy
import web.rtc.RTCDegradationPreference
import web.rtc.RTCIceCandidate
import web.rtc.RTCIceConnectionState
import web.rtc.RTCIceGatheringState
import web.rtc.RTCIceServer
import web.rtc.RTCIceTransportPolicy
import web.rtc.RTCLocalIceCandidateInit
import web.rtc.RTCLocalSessionDescriptionInit
import web.rtc.RTCPeerConnectionState
import web.rtc.RTCRtcpMuxPolicy
import web.rtc.RTCSdpType
import web.rtc.RTCSessionDescription
import web.rtc.RTCSessionDescriptionInit
import web.rtc.RTCSignalingState
import web.rtc.RTCStats
import web.rtc.RTCStatsReport
import kotlin.js.undefined

// Mapping from Browser interfaces for the web platform

internal fun WebRtcMedia.FacingMode.toJs(): JsString = when (this) {
    WebRtcMedia.FacingMode.USER -> "user"
    WebRtcMedia.FacingMode.LEFT -> "left"
    WebRtcMedia.FacingMode.RIGHT -> "right"
    WebRtcMedia.FacingMode.ENVIRONMENT -> "environment"
}.toJsString()

internal fun WebRtcMedia.ResizeMode.toJs(): JsString = when (this) {
    WebRtcMedia.ResizeMode.NONE -> "none"
    WebRtcMedia.ResizeMode.CROP_AND_SCALE -> "crop-and-scale"
}.toJsString()

internal fun WebRtc.BundlePolicy.toJs(): RTCBundlePolicy = when (this) {
    WebRtc.BundlePolicy.BALANCED -> RTCBundlePolicy.balanced
    WebRtc.BundlePolicy.MAX_BUNDLE -> RTCBundlePolicy.maxBundle
    WebRtc.BundlePolicy.MAX_COMPAT -> RTCBundlePolicy.maxCompat
}

internal fun WebRtc.RTCPMuxPolicy.toJs(): RTCRtcpMuxPolicy = when (this) {
    WebRtc.RTCPMuxPolicy.REQUIRE -> RTCRtcpMuxPolicy.require
    else -> error("Unknown RTCP mux policy: $this")
}

internal fun WebRtc.IceTransportPolicy.toJs(): RTCIceTransportPolicy = when (this) {
    WebRtc.IceTransportPolicy.ALL -> RTCIceTransportPolicy.all
    WebRtc.IceTransportPolicy.RELAY -> RTCIceTransportPolicy.relay
}

internal fun WebRtc.SessionDescriptionType.toJs(): RTCSdpType = when (this) {
    WebRtc.SessionDescriptionType.OFFER -> RTCSdpType.offer
    WebRtc.SessionDescriptionType.ANSWER -> RTCSdpType.answer
    WebRtc.SessionDescriptionType.ROLLBACK -> RTCSdpType.rollback
    WebRtc.SessionDescriptionType.PROVISIONAL_ANSWER -> RTCSdpType.pranswer
}

internal fun String.toTrackKind(): WebRtcMedia.TrackType = when (this) {
    "audio" -> WebRtcMedia.TrackType.AUDIO
    "video" -> WebRtcMedia.TrackType.VIDEO
    else -> error("Unknown media track kind: $this")
}

internal fun RTCSdpType.toKtor(): WebRtc.SessionDescriptionType = when (this) {
    RTCSdpType.offer -> WebRtc.SessionDescriptionType.OFFER
    RTCSdpType.answer -> WebRtc.SessionDescriptionType.ANSWER
    RTCSdpType.pranswer -> WebRtc.SessionDescriptionType.PROVISIONAL_ANSWER
    RTCSdpType.rollback -> WebRtc.SessionDescriptionType.ROLLBACK
}

internal fun RTCDegradationPreference?.toKtor(): WebRtc.DegradationPreference = when (this) {
    RTCDegradationPreference.maintainResolution -> WebRtc.DegradationPreference.MAINTAIN_RESOLUTION
    RTCDegradationPreference.maintainFramerate -> WebRtc.DegradationPreference.MAINTAIN_FRAMERATE
    RTCDegradationPreference.balanced -> WebRtc.DegradationPreference.BALANCED
    null -> WebRtc.DegradationPreference.DISABLED
}

internal fun RTCIceConnectionState?.toKtor(): WebRtc.IceConnectionState = when (this) {
    RTCIceConnectionState.new -> WebRtc.IceConnectionState.NEW
    RTCIceConnectionState.checking -> WebRtc.IceConnectionState.CHECKING
    RTCIceConnectionState.connected -> WebRtc.IceConnectionState.CONNECTED
    RTCIceConnectionState.disconnected -> WebRtc.IceConnectionState.DISCONNECTED
    RTCIceConnectionState.failed -> WebRtc.IceConnectionState.FAILED
    RTCIceConnectionState.closed -> WebRtc.IceConnectionState.CLOSED
    RTCIceGatheringState.complete -> WebRtc.IceConnectionState.COMPLETED
    else -> error("Unknown ice connection state: $this")
}

internal fun RTCPeerConnectionState?.toKtor(): WebRtc.ConnectionState = when (this) {
    RTCPeerConnectionState.new -> WebRtc.ConnectionState.NEW
    RTCPeerConnectionState.connecting -> WebRtc.ConnectionState.CONNECTING
    RTCPeerConnectionState.connected -> WebRtc.ConnectionState.CONNECTED
    RTCPeerConnectionState.failed -> WebRtc.ConnectionState.FAILED
    RTCPeerConnectionState.closed -> WebRtc.ConnectionState.CLOSED
    RTCPeerConnectionState.disconnected -> WebRtc.ConnectionState.DISCONNECTED
    else -> error("Unknown connection state: $this")
}

internal fun RTCIceGatheringState?.toKtor(): WebRtc.IceGatheringState = when (this) {
    RTCIceGatheringState.new -> WebRtc.IceGatheringState.NEW
    RTCIceGatheringState.gathering -> WebRtc.IceGatheringState.GATHERING
    RTCIceGatheringState.complete -> WebRtc.IceGatheringState.COMPLETE
    else -> error("Unknown ice gathering state: $this")
}

internal fun RTCSignalingState?.toKtor(): WebRtc.SignalingState = when (this) {
    RTCSignalingState.stable -> WebRtc.SignalingState.STABLE
    RTCSignalingState.haveLocalOffer -> WebRtc.SignalingState.HAVE_LOCAL_OFFER
    RTCSignalingState.haveRemoteOffer -> WebRtc.SignalingState.HAVE_REMOTE_OFFER
    RTCSignalingState.haveLocalPranswer -> WebRtc.SignalingState.HAVE_LOCAL_PROVISIONAL_ANSWER
    RTCSignalingState.haveRemotePranswer -> WebRtc.SignalingState.HAVE_REMOTE_PROVISIONAL_ANSWER
    RTCSignalingState.closed -> WebRtc.SignalingState.CLOSED
    else -> error("Unknown signaling state: $this")
}

internal fun RTCSessionDescription.toKtor(): WebRtc.SessionDescription = WebRtc.SessionDescription(
    sdp = sdp,
    type = type.toKtor(),
)

internal fun RTCSessionDescriptionInit.toKtor(): WebRtc.SessionDescription = WebRtc.SessionDescription(
    sdp = sdp ?: error("Missing SDP description"),
    type = type.toKtor(),
)

internal fun RTCIceCandidate.toKtor(): WebRtc.IceCandidate = WebRtc.IceCandidate(
    candidate = candidate,
    sdpMid = sdpMid ?: error("Missing ICE mid"),
    sdpMLineIndex = sdpMLineIndex?.toInt() ?: error("Missing ICE index"),
)

internal fun WebRtcMedia.AudioTrackConstraints.toJs(): MediaTrackConstraints {
    return object : MediaTrackConstraints {
        // Not available in kotlin wrappers because has limited availability
        @Suppress("UNSUED_VARIABLE")
        val volume = this@toJs.volume?.toJsDouble()

        // Not available in kotlin wrappers because has limited availability
        @Suppress("UNSUED_VARIABLE")
        val latency = this@toJs.latency?.toJsDouble()

        override val sampleRate = this@toJs.sampleRate?.toJsInt()
        override val sampleSize = this@toJs.sampleSize?.toJsInt()
        override val channelCount = this@toJs.channelCount?.toJsInt()
        override val autoGainControl = this@toJs.autoGainControl?.toJsBoolean()
        override val echoCancellation = this@toJs.echoCancellation?.toJsBoolean()
        override val noiseSuppression = this@toJs.noiseSuppression?.toJsBoolean()

        override val facingMode = undefined
        override val frameRate = undefined
        override val groupId = undefined
        override val height = undefined
        override val width = undefined
        override val deviceId = undefined
        override val backgroundBlur = undefined
        override val displaySurface = undefined
        override val aspectRatio = undefined
        override val advanced = undefined
    }
}

internal fun WebRtcMedia.VideoTrackConstraints.toJs(): MediaTrackConstraints {
    return object : MediaTrackConstraints {
        // Not available in kotlin wrappers because has limited availability
        @Suppress("UNSUED_VARIABLE")
        val resizeMode = this@toJs.resizeMode?.toJs()
        override val width = this@toJs.width?.toJsInt()
        override val height = this@toJs.height?.toJsInt()
        override val frameRate = this@toJs.frameRate?.toJsInt()
        override val aspectRatio = this@toJs.aspectRatio?.toJsDouble()
        override val facingMode = this@toJs.facingMode?.toJs()

        override val autoGainControl = undefined
        override val echoCancellation = undefined
        override val noiseSuppression = undefined
        override val sampleRate = undefined
        override val sampleSize = undefined
        override val channelCount = undefined
        override val groupId = undefined
        override val deviceId = undefined
        override val backgroundBlur = undefined
        override val displaySurface = undefined
        override val advanced = undefined
    }
}

internal fun WebRtc.SessionDescription.toJs(): RTCSessionDescriptionInit {
    return object : RTCSessionDescriptionInit {
        override val sdp = this@toJs.sdp
        override val type = this@toJs.type.toJs()
    }
}

internal fun WebRtc.SessionDescription.toJsLocal(): RTCLocalSessionDescriptionInit {
    return object : RTCLocalSessionDescriptionInit {
        override val sdp = this@toJsLocal.sdp
        override val type = this@toJsLocal.type.toJs()
    }
}

internal fun WebRtc.IceCandidate.toJs(): RTCLocalIceCandidateInit {
    return object : RTCLocalIceCandidateInit {
        override val sdpMLineIndex = this@toJs.sdpMLineIndex.toShort()
        override val candidate = this@toJs.candidate
        override val usernameFragment = undefined
        override val sdpMid = this@toJs.sdpMid
    }
}

internal fun WebRtc.IceServer.toJs(): RTCIceServer {
    return object : RTCIceServer {
        override val urls: JsAny = this@toJs.urls.toJsString()
        override val username: String? = this@toJs.username
        override val credential: String? = this@toJs.credential
    }
}

/**
 * Converts a browser RTCStatsReport to a list of common WebRtc.Stats objects.
 * Extracts values from the report map and converts each entry to the common format.
 */
internal fun RTCStatsReport.toKtor(): List<WebRtc.Stats> {
    val statsList = mutableListOf<WebRtc.Stats>()
    // iteration though values() fails to compile for Wasm
    forEach { value, _ ->
        val rtcStats = (value as? RTCStats) ?: return@forEach
        statsList.add(mapStatsEntry(rtcStats))
    }
    return statsList
}

private fun mapStatsEntry(stats: RTCStats): WebRtc.Stats {
    val props = Object.entries(stats).toArray().associate { (k, v) -> k.toString() to v.toString() }
    return WebRtc.Stats(
        id = stats.id,
        props = props,
        type = stats.type.toString(),
        timestamp = stats.timestamp.toDouble().toLong(),
    )
}
