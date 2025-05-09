/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("unused")

package io.ktor.webrtc.client.peer

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.ArrayBufferView
import org.w3c.dom.EventInit
import org.w3c.dom.MessageEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import org.w3c.dom.mediacapture.MediaStream
import org.w3c.dom.mediacapture.MediaStreamTrack
import org.w3c.files.Blob
import kotlin.js.Promise

public external interface RTCAnswerOptions : RTCOfferAnswerOptions

public external interface RTCConfiguration : JsAny {
    public var iceServers: JsArray<RTCIceServer>?
        get() = definedExternally
        set(value) = definedExternally
    public var iceTransportPolicy: JsString? /* "all" | "relay" */
        get() = definedExternally
        set(value) = definedExternally
    public var bundlePolicy: JsString? /* "balanced" | "max-bundle" | "max-compat" */
        get() = definedExternally
        set(value) = definedExternally
    public var rtcpMuxPolicy: JsString? /* "negotiate" | "require" */
        get() = definedExternally
        set(value) = definedExternally
    public var peerIdentity: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var certificates: JsArray<RTCCertificate>?
        get() = definedExternally
        set(value) = definedExternally
    public var iceCandidatePoolSize: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCDTMFToneChangeEventInit : EventInit {
    public var tone: JsString
}

public external interface RTCDataChannelEventInit : EventInit {
    public var channel: RTCDataChannel
}

public external interface RTCDataChannelInit : JsAny {
    public var ordered: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var maxPacketLifeTime: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var maxRetransmits: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var protocol: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var negotiated: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var id: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var priority: JsString? /* "high" | "low" | "medium" | "very-low" */
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCDtlsFingerprint : JsAny {
    public var algorithm: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var value: JsString?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCErrorEventInit : EventInit {
    public var error: RTCError
}

public external interface RTCErrorInit : JsAny {
    public var errorDetail: JsString /* "data-channel-failure" | "dtls-failure" | "fingerprint-failure" | "hardware-encoder-error" | "hardware-encoder-not-available" | "idp-bad-script-failure" | "idp-execution-failure" | "idp-load-failure" | "idp-need-login" | "idp-timeout" | "idp-tls-failure" | "idp-token-expired" | "idp-token-invalid" | "sctp-failure" | "sdp-syntax-error" */
    public var httpRequestStatusCode: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var receivedAlert: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var sctpCauseCode: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var sdpLineNumber: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var sentAlert: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCIceCandidateComplete

public external interface RTCIceCandidateDictionary : JsAny {
    public var foundation: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var ip: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var msMTurnSessionId: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var port: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var priority: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var protocol: JsString? /* "tcp" | "udp" */
        get() = definedExternally
        set(value) = definedExternally
    public var relatedAddress: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var relatedPort: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var tcpType: JsString? /* "active" | "passive" | "so" */
        get() = definedExternally
        set(value) = definedExternally
    public var type: JsString? /* "host" | "prflx" | "relay" | "srflx" */
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCIceCandidateInit : JsAny {
    public var candidate: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var sdpMLineIndex: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var sdpMid: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var usernameFragment: JsString?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCIceCandidatePair : JsAny {
    public var local: RTCIceCandidate?
        get() = definedExternally
        set(value) = definedExternally
    public var remote: RTCIceCandidate?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCIceParameters : JsAny {
    public var iceLite: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var password: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var usernameFragment: JsString?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCIceServer : JsAny {
    public var urls: JsAny? /* JsString | JsArray<JsString> */
        get() = definedExternally
        set(value) = definedExternally
    public var username: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var credential: JsAny? /* JsString? | RTCOAuthCredential? */
        get() = definedExternally
        set(value) = definedExternally
    public var credentialType: JsString? /* "oauth" | "password" */
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCIdentityProviderOptions : JsAny {
    public var peerIdentity: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var protocol: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var usernameHint: JsString?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCOAuthCredential : JsAny {
    public var accessToken: JsString
    public var macKey: JsString
}

public external interface RTCOfferAnswerOptions : JsAny {
    public var voiceActivityDetection: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCOfferOptions : RTCOfferAnswerOptions {
    public var iceRestart: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var offerToReceiveAudio: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var offerToReceiveVideo: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCPeerConnectionIceErrorEventInit : EventInit {
    public var errorCode: JsNumber
    public var hostCandidate: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var statusText: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var url: JsString?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCPeerConnectionIceEventInit : EventInit {
    public var candidate: RTCIceCandidate?
        get() = definedExternally
        set(value) = definedExternally
    public var url: JsString?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtcpParameters : JsAny {
    public var cname: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var reducedSize: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpCapabilities : JsAny {
    public var codecs: JsArray<RTCRtpCodecCapability>
    public var headerExtensions: JsArray<RTCRtpHeaderExtensionCapability>
}

public external interface RTCRtpCodecCapability : JsAny {
    public var mimeType: JsString
    public var channels: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var clockRate: JsNumber
    public var sdpFmtpLine: JsString?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpCodecParameters : JsAny {
    public var mimeType: JsString
    public var channels: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var sdpFmtpLine: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var clockRate: JsNumber
    public var payloadType: JsNumber
}

public external interface RTCRtpCodingParameters : JsAny {
    public var rid: JsString?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpContributingSource : JsAny {
    public var source: JsNumber
    public var voiceActivityFlag: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var audioLevel: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var rtpTimestamp: JsNumber
    public var timestamp: JsNumber
}

public external interface RTCRtpDecodingParameters : RTCRtpCodingParameters

public external interface RTCRtpEncodingParameters : RTCRtpCodingParameters {
    public var scaleResolutionDownBy: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var active: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var codecPayloadType: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var dtx: JsString? /* "disabled" | "enabled" */
        get() = definedExternally
        set(value) = definedExternally
    public var maxBitrate: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var maxFramerate: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var ptime: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpFecParameters : JsAny {
    public var mechanism: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var ssrc: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpHeaderExtensionCapability : JsAny {
    public var uri: JsString?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpHeaderExtensionParameters : JsAny {
    public var encrypted: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var id: JsNumber
    public var uri: JsString
}

public external interface RTCRtpParameters : JsAny {
    public var transactionId: JsString
    public var codecs: JsArray<RTCRtpCodecParameters>
    public var headerExtensions: JsArray<RTCRtpHeaderExtensionParameters>
    public var rtcp: RTCRtcpParameters
}

public external interface RTCRtpReceiveParameters : RTCRtpParameters {
    public var encodings: JsArray<RTCRtpDecodingParameters>
}

public external interface RTCRtpRtxParameters : JsAny {
    public var ssrc: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpSendParameters : RTCRtpParameters {
    public var degradationPreference: JsString? /* "balanced" | "maintain-framerate" | "maintain-resolution" */
        get() = definedExternally
        set(value) = definedExternally
    public var encodings: JsArray<RTCRtpEncodingParameters>
    public var priority: JsString? /* "high" | "low" | "medium" | "very-low" */
        get() = definedExternally
        set(value) = definedExternally
    public override var transactionId: JsString
}

public external interface RTCRtpSynchronizationSource : RTCRtpContributingSource {
    public override var voiceActivityFlag: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpTransceiverInit : JsAny {
    public var direction: JsString? /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */
        get() = definedExternally
        set(value) = definedExternally
    public var streams: JsArray<MediaStream>?
        get() = definedExternally
        set(value) = definedExternally
    public var sendEncodings: JsArray<RTCRtpEncodingParameters>?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCSessionDescriptionInit : JsAny {
    public var sdp: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var type: JsString? /* "answer" | "offer" | "pranswer" | "rollback" */
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCStatsReport : ReadonlyMap<JsString> {
    public fun forEach(
        callbackfn: (value: JsAny, key: JsString, parent: RTCStatsReport) -> JsUndefined,
        thisArg: JsAny = definedExternally
    )
}

public external interface RTCStats : JsAny {
    public val timestamp: JsNumber
    public val type: JsString
    public val id: JsString
}

public external interface RTCMediaStats : RTCStats {
    public val kind: JsString
    public val trackId: JsString
}

public external interface RTCAudioStats : RTCMediaStats {
    public val audioLevel: JsNumber?
    public val totalAudioEnergy: JsNumber?
    public val totalSamplesDuration: JsNumber?
}

public external interface RTCVideoStats : RTCMediaStats {
    public val width: JsNumber?
    public val height: JsNumber?
    public val frames: JsNumber?
    public val framesPerSecond: JsNumber?
}

public external interface RTCTrackEventInit : EventInit {
    public var receiver: RTCRtpReceiver
    public var streams: JsArray<MediaStream>?
        get() = definedExternally
        set(value) = definedExternally
    public var track: MediaStreamTrack
    public var transceiver: RTCRtpTransceiver
}

public external interface RTCCertificate : JsAny {
    public var expires: JsNumber
    public fun getAlgorithm(): JsString
    public fun getFingerprints(): JsArray<RTCDtlsFingerprint>
}

public external class RTCDTMFSender : EventTarget {
    public var canInsertDTMF: Boolean
    public var ontonechange: ((self: RTCDTMFSender, ev: RTCDTMFToneChangeEvent) -> JsAny)?
    public var toneBuffer: JsString
    public fun insertDTMF(tones: JsString, duration: JsNumber = definedExternally, interToneGap: JsNumber = definedExternally)
}

public external class RTCDTMFToneChangeEvent : Event {
    public var tone: JsString
}

public external class RTCDataChannel : EventTarget {
    public var label: JsString
    public var ordered: Boolean
    public var maxPacketLifeTime: JsNumber?
    public var maxRetransmits: JsNumber?
    public var protocol: JsString
    public var negotiated: Boolean
    public var id: JsNumber?
    public var readyState: JsString /* "closed" | "closing" | "connecting" | "open" */
    public var bufferedAmount: JsNumber
    public var bufferedAmountLowThreshold: JsNumber
    public fun close()
    public fun send(data: JsString)
    public fun send(data: Blob)
    public fun send(data: ArrayBuffer)
    public fun send(data: ArrayBufferView)
    public var onopen: ((self: RTCDataChannel, ev: Event) -> JsAny)?
    public var onmessage: ((self: RTCDataChannel, ev: MessageEvent) -> JsAny)?
    public var onbufferedamountlow: ((self: RTCDataChannel, ev: Event) -> JsAny)?
    public var onclose: ((self: RTCDataChannel, ev: Event) -> JsAny)?
    public var binaryType: JsString
    public var onerror: ((self: RTCDataChannel, ev: RTCErrorEvent) -> JsAny)?
    public var priority: JsString /* "high" | "low" | "medium" | "very-low" */
}

public external class RTCDataChannelEvent : Event {
    public var channel: RTCDataChannel
}

public external class RTCDtlsTransport : EventTarget {
    public var iceTransport: RTCIceTransport
    public var state: JsString /* "closed" | "connected" | "connecting" | "failed" | "new" */
    public fun getRemoteCertificates(): JsArray<ArrayBuffer>
    public var onerror: ((self: RTCDtlsTransport, ev: RTCErrorEvent) -> JsAny)?
    public var onstatechange: ((self: RTCDtlsTransport, ev: Event) -> JsAny)?
}

public external class RTCDtlsTransportStateChangedEvent : Event {
    public var state: JsString /* "closed" | "connected" | "connecting" | "failed" | "new" */
}

public external class RTCError : DOMException {
    public var errorDetail: JsString /* "data-channel-failure" | "dtls-failure" | "fingerprint-failure" | "hardware-encoder-error" | "hardware-encoder-not-available" | "idp-bad-script-failure" | "idp-execution-failure" | "idp-load-failure" | "idp-need-login" | "idp-timeout" | "idp-tls-failure" | "idp-token-expired" | "idp-token-invalid" | "sctp-failure" | "sdp-syntax-error" */
    public var httpRequestStatusCode: JsNumber?
    public var receivedAlert: JsNumber?
    public var sctpCauseCode: JsNumber?
    public var sdpLineNumber: JsNumber?
    public var sentAlert: JsNumber?
}

public external class RTCErrorEvent : Event {
    public var error: RTCError
}

public external class RTCIceCandidate : JsAny {
    public constructor(init: RTCIceCandidateInit)

    public var candidate: JsString
    public var component: JsString /* "rtcp" | "rtp" */
    public var foundation: JsString?
    public var port: JsNumber?
    public var priority: JsNumber?
    public var protocol: JsString /* "tcp" | "udp" */
    public var relatedAddress: JsString?
    public var relatedPort: JsNumber?
    public var sdpMLineIndex: JsNumber?
    public var sdpMid: JsString?
    public var tcpType: JsString /* "active" | "passive" | "so" */
    public var type: JsString /* "host" | "prflx" | "relay" | "srflx" */
    public var usernameFragment: JsString?
    public fun toJSON(): RTCIceCandidateInit
}

public external class RTCIceCandidatePairChangedEvent : Event {
    public var pair: RTCIceCandidatePair
}

public external class RTCIceGathererEvent : Event {
    public var candidate: JsAny? /* RTCIceCandidateDictionary | RTCIceCandidateComplete */
        get() = definedExternally
        set(value) = definedExternally
}

public external class RTCIceTransport : EventTarget {
    public var role: JsString /* "controlled" | "controlling" | "unknown" */
    public var gatheringState: JsString /* "complete" | "gathering" | "new" */
    public fun getLocalCandidates(): JsArray<RTCIceCandidate>
    public fun getRemoteCandidates(): JsArray<RTCIceCandidate>
    public fun getLocalParameters(): RTCIceParameters?
    public fun getRemoteParameters(): RTCIceParameters?
    public fun getSelectedCandidatePair(): RTCIceCandidatePair?
    public var onstatechange: ((self: RTCIceTransport, ev: Event) -> JsAny)?
    public var ongatheringstatechange: ((self: RTCIceTransport, ev: Event) -> JsAny)?
    public var onselectedcandidatepairchange: ((self: RTCIceTransport, ev: Event) -> JsAny)?
    public var component: JsString /* "rtcp" | "rtp" */
    public var state: JsString /* "checking" | "closed" | "completed" | "connected" | "disconnected" | "failed" | "new" */
}

public external class RTCIceTransportStateChangedEvent : Event {
    public var state: JsString /* "checking" | "closed" | "completed" | "connected" | "disconnected" | "failed" | "new" */
}

public external class RTCIdentityAssertion : JsAny {
    public var idp: JsString
    public var name: JsString
}

public external class RTCPeerConnection(config: RTCConfiguration) : EventTarget {
    public fun createOffer(options: RTCOfferOptions = definedExternally): Promise<RTCSessionDescriptionInit>
    public fun createOffer(): Promise<RTCSessionDescriptionInit>
    public fun createAnswer(options: RTCAnswerOptions = definedExternally): Promise<RTCSessionDescriptionInit>
    public fun createAnswer(): Promise<RTCSessionDescriptionInit>
    public fun setLocalDescription(description: RTCSessionDescription): Promise<JsAny?>
    public var localDescription: RTCSessionDescription?
    public var currentLocalDescription: RTCSessionDescription?
    public var pendingLocalDescription: RTCSessionDescription?
    public fun setRemoteDescription(description: RTCSessionDescription): Promise<JsUndefined>
    public var remoteDescription: RTCSessionDescription?
    public var currentRemoteDescription: RTCSessionDescription?
    public var pendingRemoteDescription: RTCSessionDescription?
    public fun addIceCandidate(candidate: RTCIceCandidateInit = definedExternally): Promise<JsUndefined>
    public fun addIceCandidate(): Promise<JsUndefined>
    public fun addIceCandidate(candidate: RTCIceCandidate = definedExternally): Promise<JsUndefined>
    public var signalingState: JsString /* "closed" | "have-local-offer" | "have-local-pranswer" | "have-remote-offer" | "have-remote-pranswer" | "stable" */
    public var connectionState: JsString /* "closed" | "connected" | "connecting" | "disconnected" | "failed" | "new" */
    public fun getConfiguration(): RTCConfiguration
    public fun setConfiguration(configuration: RTCConfiguration)
    public fun close()
    public var onicecandidateerror: ((self: RTCPeerConnection, ev: RTCPeerConnectionIceErrorEvent) -> Unit)?
    public var onconnectionstatechange: ((self: RTCPeerConnection, ev: Event) -> Unit)?
    public fun getSenders(): JsArray<RTCRtpSender>
    public fun getReceivers(): JsArray<RTCRtpReceiver>
    public fun getTransceivers(): JsArray<RTCRtpTransceiver>
    public fun addTrack(track: MediaStreamTrack, vararg streams: MediaStream): RTCRtpSender
    public fun removeTrack(sender: RTCRtpSender)
    public fun addTransceiver(
        trackOrKind: MediaStreamTrack,
        init: RTCRtpTransceiverInit = definedExternally
    ): RTCRtpTransceiver

    public fun addTransceiver(trackOrKind: MediaStreamTrack): RTCRtpTransceiver
    public fun addTransceiver(trackOrKind: JsString, init: RTCRtpTransceiverInit = definedExternally): RTCRtpTransceiver
    public fun addTransceiver(trackOrKind: JsString): RTCRtpTransceiver
    public var ontrack: ((self: RTCPeerConnection, ev: RTCTrackEvent) -> JsAny)?
    public var sctp: RTCSctpTransport?
    public fun createDataChannel(
        label: JsString?,
        dataChannelDict: RTCDataChannelInit = definedExternally
    ): RTCDataChannel

    public fun createDataChannel(label: JsString?): RTCDataChannel
    public var ondatachannel: ((self: RTCPeerConnection, ev: RTCDataChannelEvent) -> JsAny)?
    public fun getStats(selector: MediaStreamTrack? = definedExternally): Promise<RTCStatsReport>
    public fun getStats(): Promise<RTCStatsReport>
    public fun createOffer(
        successCallback: RTCSessionDescriptionCallback,
        failureCallback: RTCPeerConnectionErrorCallback,
        options: RTCOfferOptions = definedExternally
    ): Promise<JsUndefined>

    public fun createOffer(
        successCallback: RTCSessionDescriptionCallback,
        failureCallback: RTCPeerConnectionErrorCallback
    ): Promise<JsUndefined>

    public fun setLocalDescription(
        description: RTCSessionDescriptionInit,
        successCallback: () -> JsUndefined,
        failureCallback: RTCPeerConnectionErrorCallback
    ): Promise<JsUndefined>

    public fun createAnswer(
        successCallback: RTCSessionDescriptionCallback,
        failureCallback: RTCPeerConnectionErrorCallback
    ): Promise<JsUndefined>

    public fun setRemoteDescription(
        description: RTCSessionDescriptionInit,
        successCallback: () -> JsUndefined,
        failureCallback: RTCPeerConnectionErrorCallback
    ): Promise<JsUndefined>

    public fun addIceCandidate(
        candidate: RTCIceCandidateInit,
        successCallback: () -> JsUndefined,
        failureCallback: RTCPeerConnectionErrorCallback
    ): Promise<JsUndefined>

    public fun addIceCandidate(
        candidate: RTCIceCandidate,
        successCallback: () -> JsUndefined,
        failureCallback: RTCPeerConnectionErrorCallback
    ): Promise<JsUndefined>

    public fun getStats(
        selector: MediaStreamTrack?,
        successCallback: (report: RTCStatsReport) -> JsUndefined,
        failureCallback: RTCPeerConnectionErrorCallback
    ): Promise<JsUndefined>

    public var canTrickleIceCandidates: Boolean?
    public var iceConnectionState: JsString /* "checking" | "closed" | "completed" | "connected" | "disconnected" | "failed" | "new" */
    public var iceGatheringState: JsString /* "complete" | "gathering" | "new" */
    public var idpErrorInfo: JsString?
    public var idpLoginUrl: JsString?
    public var onicecandidate: ((ev: RTCPeerConnectionIceEvent) -> Unit)?
    public var oniceconnectionstatechange: ((ev: Event) -> Unit)?
    public var onicegatheringstatechange: ((ev: Event) -> Unit)?
    public var onnegotiationneeded: ((ev: Event) -> Unit)?
    public var onsignalingstatechange: ((ev: Event) -> Unit)?
    public var onstatsended: ((ev: RTCStatsEvent) -> Unit)?
    public var peerIdentity: Promise<RTCIdentityAssertion>
    public fun createAnswer(options: RTCOfferOptions = definedExternally): Promise<RTCSessionDescriptionInit>
    public fun createDataChannel(label: JsString, dataChannelDict: RTCDataChannelInit = definedExternally): RTCDataChannel
    public fun createDataChannel(label: JsString): RTCDataChannel
    public fun getIdentityAssertion(): Promise<JsString>
    public fun setIdentityProvider(provider: JsString, options: RTCIdentityProviderOptions = definedExternally)
}

public external class RTCPeerConnectionIceErrorEvent : Event {
    public var hostCandidate: JsString
    public var url: JsString
    public var errorCode: JsNumber
    public var errorText: JsString
}

public external class RTCPeerConnectionIceEvent : Event {
    public var url: JsString?
    public var candidate: RTCIceCandidate?
}

public external interface RTCRtpReceiver : JsAny {
    public fun getParameters(): JsAny? /* RTCRtpParameters | RTCRtpReceiveParameters */
    public fun getContributingSources(): JsArray<RTCRtpContributingSource>
    public var rtcpTransport: RTCDtlsTransport?
    public var track: MediaStreamTrack
    public var transport: RTCDtlsTransport?
    public fun getStats(): Promise<RTCStatsReport>
    public fun getSynchronizationSources(): JsArray<RTCRtpSynchronizationSource>
}

public external interface RTCRtpSender : JsAny {
    public fun setParameters(parameters: RTCRtpParameters = definedExternally): Promise<JsUndefined>
    public fun setParameters(): Promise<JsAny?>
    public fun getParameters(): JsAny? /* RTCRtpParameters | RTCRtpSendParameters */
    public fun replaceTrack(withTrack: MediaStreamTrack): Promise<JsUndefined>
    public var dtmf: RTCDTMFSender?
    public var rtcpTransport: RTCDtlsTransport?
    public var track: MediaStreamTrack?
    public var transport: RTCDtlsTransport?
    public fun getStats(): Promise<RTCStatsReport>
    public fun replaceTrack(withTrack: MediaStreamTrack?): Promise<JsUndefined>
    public fun setParameters(parameters: RTCRtpSendParameters): Promise<JsUndefined>
    public fun setStreams(vararg streams: MediaStream)
}

public external interface RTCRtpTransceiver : JsAny {
    public var mid: JsString?
    public var sender: RTCRtpSender
    public var receiver: RTCRtpReceiver
    public var stopped: Boolean
    public var direction: JsString /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */
    public fun setDirection(direction: JsString /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */)
    public fun stop()
    public fun setCodecPreferences(codecs: JsArray<RTCRtpCodecCapability>)
    public var currentDirection: JsString /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */
}

public external class RTCSctpTransport private constructor() : EventTarget {
    public var transport: RTCDtlsTransport
    public var maxMessageSize: JsNumber
    public var maxChannels: JsNumber?
    public var onstatechange: ((self: RTCSctpTransport, ev: Event) -> JsAny)?
    public var state: JsString /* "closed" | "connected" | "connecting" */
}

public external class RTCSessionDescription : JsAny {
    public var sdp: JsString
    public var type: JsString /* "answer" | "offer" | "pranswer" | "rollback" */
    public fun toJSON(): JsAny
}

public external class RTCSsrcConflictEvent : Event {
    public var ssrc: JsNumber
}

public external class RTCStatsEvent : Event {
    public var report: RTCStatsReport
}

public external class RTCTrackEvent : Event {
    public var receiver: RTCRtpReceiver
    public var track: MediaStreamTrack
    public var streams: JsArray<MediaStream>
    public var transceiver: RTCRtpTransceiver
}

public external interface RTCPeerConnectionStatic : JsAny {
    public var defaultIceServers: JsArray<RTCIceServer>
    public fun generateCertificate(keygenAlgorithm: JsString): Promise<RTCCertificate>
}

public typealias RTCPeerConnectionErrorCallback = (error: DOMException) -> Unit

public typealias RTCSessionDescriptionCallback = (description: RTCSessionDescriptionInit) -> Unit
