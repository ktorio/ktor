/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.webrtc

import js.array.component1
import js.array.component2
import js.core.JsPrimitives.toDouble
import js.core.JsPrimitives.toJsBoolean
import js.core.JsPrimitives.toJsDouble
import js.core.JsPrimitives.toJsInt
import js.core.JsPrimitives.toJsString
import js.core.JsString
import js.objects.Object
import js.objects.unsafeJso
import js.reflect.unsafeCast
import web.mediastreams.ConstrainDouble
import web.mediastreams.MediaTrackConstraints
import web.rtc.*

// Mapping from Browser interfaces for the web platform
// TODO: add missing fields in the `kotlin-wrappers` api

internal fun WebRtcConnectionConfig.toJs(): RTCConfiguration = unsafeJso {
    bundlePolicy = this@toJs.bundlePolicy.toJs()
    rtcpMuxPolicy = this@toJs.rtcpMuxPolicy.toJs()
    iceTransportPolicy = this@toJs.iceTransportPolicy.toJs()
    iceServers = this@toJs.iceServers.map { it.toJs() }.toJs()
    iceCandidatePoolSize = this@toJs.iceCandidatePoolSize.toShort()
}

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

internal fun WebRtc.RtcpMuxPolicy.toJs(): RTCRtcpMuxPolicy = when (this) {
    WebRtc.RtcpMuxPolicy.REQUIRE -> RTCRtcpMuxPolicy.require
    WebRtc.RtcpMuxPolicy.NEGOTIATE -> unsafeCast("negotiate")
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

internal fun RTCDataChannelState.toKtor(): WebRtc.DataChannel.State = when (this) {
    RTCDataChannelState.connecting -> WebRtc.DataChannel.State.CONNECTING
    RTCDataChannelState.open -> WebRtc.DataChannel.State.OPEN
    RTCDataChannelState.closing -> WebRtc.DataChannel.State.CLOSING
    RTCDataChannelState.closed -> WebRtc.DataChannel.State.CLOSED
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

internal external interface AudioTrackConstraints : MediaTrackConstraints {
    var volume: ConstrainDouble?
    var latency: ConstrainDouble?
}

internal fun WebRtcMedia.AudioTrackConstraints.toJs(): MediaTrackConstraints = unsafeJso<AudioTrackConstraints> {
    volume = this@toJs.volume?.toJsDouble()
    latency = this@toJs.latency?.toJsDouble()
    sampleRate = this@toJs.sampleRate?.toJsInt()
    sampleSize = this@toJs.sampleSize?.toJsInt()
    channelCount = this@toJs.channelCount?.toJsInt()
    autoGainControl = this@toJs.autoGainControl?.toJsBoolean()
    echoCancellation = this@toJs.echoCancellation?.toJsBoolean()
    noiseSuppression = this@toJs.noiseSuppression?.toJsBoolean()
}

internal external interface VideoTrackSettings : MediaTrackConstraints {
    var resizeMode: JsString?
}

internal fun WebRtcMedia.VideoTrackConstraints.toJs(): MediaTrackConstraints = unsafeJso<VideoTrackSettings> {
    resizeMode = this@toJs.resizeMode?.toJs()
    width = this@toJs.width?.toJsInt()
    height = this@toJs.height?.toJsInt()
    frameRate = this@toJs.frameRate?.toJsInt()
    aspectRatio = this@toJs.aspectRatio?.toJsDouble()
    facingMode = this@toJs.facingMode?.toJs()
}

internal fun WebRtc.SessionDescription.toJs(): RTCSessionDescriptionInit = unsafeJso {
    sdp = this@toJs.sdp
    type = this@toJs.type.toJs()
}

internal fun WebRtc.SessionDescription.toJsLocal(): RTCLocalSessionDescriptionInit = unsafeJso {
    sdp = this@toJsLocal.sdp
    type = this@toJsLocal.type.toJs()
}

internal fun WebRtc.IceCandidate.toJs(): RTCLocalIceCandidateInit = unsafeJso {
    sdpMLineIndex = this@toJs.sdpMLineIndex.toShort()
    candidate = this@toJs.candidate
    sdpMid = this@toJs.sdpMid
}

internal fun WebRtc.IceServer.toJs(): RTCIceServer = unsafeJso {
    urls = this@toJs.urls.toJsString()
    username = this@toJs.username
    credential = this@toJs.credential
}

internal fun WebRtcDataChannelOptions.toJs(): RTCDataChannelInit = unsafeJso {
    id = this@toJs.id?.toShort()
    ordered = this@toJs.ordered
    protocol = this@toJs.protocol
    negotiated = this@toJs.negotiated
    if (this@toJs.maxRetransmits != null) {
        maxRetransmits = this@toJs.maxRetransmits!!.toShort()
    }
    if (this@toJs.maxPacketLifeTime != null) {
        maxPacketLifeTime = this@toJs.maxPacketLifeTime?.inWholeMilliseconds?.toShort()
    }
}

/**
 * Converts a browser RTCStatsReport to a list of common WebRtc.Stats objects.
 * Extracts values from the report map and converts each entry to the common format.
 */
internal fun RTCStatsReport.toKtor(): List<WebRtc.Stats> {
    val statsList = mutableListOf<WebRtc.Stats>()
    forEach { value, _ ->
        val rtcStats = value as RTCStats
        statsList.add(rtcStats.toKtor())
    }
    return statsList
}

internal fun RTCStats.toKtor(): WebRtc.Stats {
    val props = Object.entries(this).toArray().associate { (k, v) -> k.toString() to v.toString() }
    return WebRtc.Stats(
        id = id,
        props = props,
        type = type.toString(),
        timestamp = timestamp.toDouble().toLong(),
    )
}
