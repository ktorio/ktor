/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalForeignApi::class)

package io.ktor.client.webrtc

import WebRTC.*
import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import platform.AVFoundation.AVCaptureDevicePosition
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun WebRtc.SessionDescription.toIos(): RTCSessionDescription {
    return RTCSessionDescription(
        type = when (type) {
            WebRtc.SessionDescriptionType.OFFER -> RTCSdpType.RTCSdpTypeOffer
            WebRtc.SessionDescriptionType.ANSWER -> RTCSdpType.RTCSdpTypeAnswer
            WebRtc.SessionDescriptionType.PROVISIONAL_ANSWER -> RTCSdpType.RTCSdpTypePrAnswer
            WebRtc.SessionDescriptionType.ROLLBACK -> RTCSdpType.RTCSdpTypeRollback
        },
        sdp = sdp
    )
}

internal fun WebRtc.BundlePolicy.toIos(): RTCBundlePolicy = when (this) {
    WebRtc.BundlePolicy.BALANCED -> RTCBundlePolicy.RTCBundlePolicyBalanced
    WebRtc.BundlePolicy.MAX_BUNDLE -> RTCBundlePolicy.RTCBundlePolicyMaxBundle
    WebRtc.BundlePolicy.MAX_COMPAT -> RTCBundlePolicy.RTCBundlePolicyMaxCompat
}

internal fun WebRtc.IceTransportPolicy.toIos(): RTCIceTransportPolicy = when (this) {
    WebRtc.IceTransportPolicy.ALL -> RTCIceTransportPolicy.RTCIceTransportPolicyAll
    WebRtc.IceTransportPolicy.RELAY -> RTCIceTransportPolicy.RTCIceTransportPolicyRelay
}

internal fun WebRtc.RtcpMuxPolicy.toIos(): RTCRtcpMuxPolicy = when (this) {
    WebRtc.RtcpMuxPolicy.NEGOTIATE -> RTCRtcpMuxPolicy.RTCRtcpMuxPolicyNegotiate
    WebRtc.RtcpMuxPolicy.REQUIRE -> RTCRtcpMuxPolicy.RTCRtcpMuxPolicyRequire
}

internal fun RTCIceConnectionState.toKtor(): WebRtc.IceConnectionState = when (this) {
    RTCIceConnectionState.RTCIceConnectionStateNew -> WebRtc.IceConnectionState.NEW
    RTCIceConnectionState.RTCIceConnectionStateChecking -> WebRtc.IceConnectionState.CHECKING
    RTCIceConnectionState.RTCIceConnectionStateConnected -> WebRtc.IceConnectionState.CONNECTED
    RTCIceConnectionState.RTCIceConnectionStateCompleted -> WebRtc.IceConnectionState.COMPLETED
    RTCIceConnectionState.RTCIceConnectionStateDisconnected -> WebRtc.IceConnectionState.DISCONNECTED
    RTCIceConnectionState.RTCIceConnectionStateFailed -> WebRtc.IceConnectionState.FAILED
    RTCIceConnectionState.RTCIceConnectionStateClosed -> WebRtc.IceConnectionState.CLOSED
    else -> error("Unknown RTCIceConnectionState: $this")
}

internal fun RTCPeerConnectionState.toKtor(): WebRtc.ConnectionState = when (this) {
    RTCPeerConnectionState.RTCPeerConnectionStateNew -> WebRtc.ConnectionState.NEW
    RTCPeerConnectionState.RTCPeerConnectionStateConnecting -> WebRtc.ConnectionState.CONNECTING
    RTCPeerConnectionState.RTCPeerConnectionStateConnected -> WebRtc.ConnectionState.CONNECTED
    RTCPeerConnectionState.RTCPeerConnectionStateDisconnected -> WebRtc.ConnectionState.DISCONNECTED
    RTCPeerConnectionState.RTCPeerConnectionStateFailed -> WebRtc.ConnectionState.FAILED
    RTCPeerConnectionState.RTCPeerConnectionStateClosed -> WebRtc.ConnectionState.CLOSED
    else -> error("Unknown RTCPeerConnectionState: $this")
}

internal fun RTCSignalingState.toKtor(): WebRtc.SignalingState = when (this) {
    RTCSignalingState.RTCSignalingStateStable -> WebRtc.SignalingState.STABLE
    RTCSignalingState.RTCSignalingStateClosed -> WebRtc.SignalingState.CLOSED
    RTCSignalingState.RTCSignalingStateHaveLocalOffer -> WebRtc.SignalingState.HAVE_LOCAL_OFFER
    RTCSignalingState.RTCSignalingStateHaveLocalPrAnswer -> WebRtc.SignalingState.HAVE_LOCAL_PROVISIONAL_ANSWER
    RTCSignalingState.RTCSignalingStateHaveRemoteOffer -> WebRtc.SignalingState.HAVE_REMOTE_OFFER
    RTCSignalingState.RTCSignalingStateHaveRemotePrAnswer -> WebRtc.SignalingState.HAVE_REMOTE_PROVISIONAL_ANSWER
    else -> error("Unknown RTCSignalingState: $this")
}

internal fun RTCIceGatheringState.toKtor(): WebRtc.IceGatheringState = when (this) {
    RTCIceGatheringState.RTCIceGatheringStateNew -> WebRtc.IceGatheringState.NEW
    RTCIceGatheringState.RTCIceGatheringStateGathering -> WebRtc.IceGatheringState.GATHERING
    RTCIceGatheringState.RTCIceGatheringStateComplete -> WebRtc.IceGatheringState.COMPLETE
    else -> error("Unknown RTCIceGatheringState: $this")
}

internal fun RTCDataChannelState.toKtor(): WebRtc.DataChannel.State = when (this) {
    RTCDataChannelState.RTCDataChannelStateConnecting -> WebRtc.DataChannel.State.CONNECTING
    RTCDataChannelState.RTCDataChannelStateClosing -> WebRtc.DataChannel.State.CLOSING
    RTCDataChannelState.RTCDataChannelStateClosed -> WebRtc.DataChannel.State.CLOSED
    RTCDataChannelState.RTCDataChannelStateOpen -> WebRtc.DataChannel.State.OPEN
    else -> error("Unknown RTCDataChannelState: $this")
}

internal fun RTCSessionDescription.toKtor(): WebRtc.SessionDescription {
    return WebRtc.SessionDescription(
        when (type) {
            RTCSdpType.RTCSdpTypeOffer -> WebRtc.SessionDescriptionType.OFFER
            RTCSdpType.RTCSdpTypeAnswer -> WebRtc.SessionDescriptionType.ANSWER
            RTCSdpType.RTCSdpTypeRollback -> WebRtc.SessionDescriptionType.ROLLBACK
            RTCSdpType.RTCSdpTypePrAnswer -> WebRtc.SessionDescriptionType.PROVISIONAL_ANSWER
            else -> error("Unknown RTCSdpType: $type")
        },
        sdp
    )
}

internal fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).apply {
    usePinned { memcpy(it.addressOf(0), bytes, length) }
}

@OptIn(BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData = memScoped<NSData> {
    return NSData.create(bytes = this@toNSData.toCValues().ptr, length = size.toULong())
}

internal fun ByteArray.toRTCDataBuffer(): RTCDataBuffer {
    return RTCDataBuffer(data = toNSData(), isBinary = true)
}

internal fun String.toRTCDataBuffer(): RTCDataBuffer {
    return RTCDataBuffer(data = toByteArray().toNSData(), isBinary = false)
}

internal fun RTCDataBuffer.toKtor(): WebRtc.DataChannel.Message = when (isBinary) {
    true -> WebRtc.DataChannel.Message.Binary(data.toByteArray())
    false -> WebRtc.DataChannel.Message.Text(data.toByteArray().decodeToString())
}

internal fun RTCStatisticsReport.toKtor(): List<WebRtc.Stats> = statistics.values.map { it ->
    val stats = it as RTCStatistics
    WebRtc.Stats(
        id = stats.id,
        type = stats.type,
        timestamp = stats.timestamp_us.toLong(),
        props = stats.values.map { it.key.toString() to it.value }.toMap()
    )
}

internal fun RTCIceCandidate.toKtor(): WebRtc.IceCandidate = WebRtc.IceCandidate(
    candidate = sdp,
    sdpMLineIndex = sdpMLineIndex,
    sdpMid = sdpMid ?: error("SDP mid is required for candidate"),
)

internal fun WebRtc.IceCandidate.toIos(): RTCIceCandidate = RTCIceCandidate(
    sdp = candidate,
    sdpMid = sdpMid,
    sdpMLineIndex = sdpMLineIndex,
)

internal fun WebRtcMedia.FacingMode.toIos(): AVCaptureDevicePosition {
    return when (this) {
        WebRtcMedia.FacingMode.USER -> AVCaptureDevicePositionFront
        WebRtcMedia.FacingMode.ENVIRONMENT -> AVCaptureDevicePositionBack
        else -> error("Unsupported facing mode: $this")
    }
}

internal fun Continuation<RTCSessionDescription>.toSdpCreateHandler() =
    { sdp: RTCSessionDescription?, error: NSError? ->
        when {
            error != null -> resumeWithException(WebRtc.SdpException(message = error.toString()))
            sdp == null -> resumeWithException(WebRtc.SdpException(message = "Failed to create session description."))
            else -> resume(sdp)
        }
    }

internal fun Continuation<Unit>.toSdpSetHandler() = { error: NSError? ->
    when {
        error != null -> resumeWithException(WebRtc.SdpException(message = error.toString()))
        else -> resume(Unit)
    }
}
