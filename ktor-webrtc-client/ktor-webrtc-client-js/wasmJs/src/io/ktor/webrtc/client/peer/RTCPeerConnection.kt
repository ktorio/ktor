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
    public var iceTransportPolicy: String? /* "all" | "relay" */
        get() = definedExternally
        set(value) = definedExternally
    public var bundlePolicy: String? /* "balanced" | "max-bundle" | "max-compat" */
        get() = definedExternally
        set(value) = definedExternally
    public var rtcpMuxPolicy: String? /* "negotiate" | "require" */
        get() = definedExternally
        set(value) = definedExternally
    public var peerIdentity: String?
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
    public var tone: String
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
    public var protocol: String?
        get() = definedExternally
        set(value) = definedExternally
    public var negotiated: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var id: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var priority: String? /* "high" | "low" | "medium" | "very-low" */
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCDtlsFingerprint : JsAny {
    public var algorithm: String?
        get() = definedExternally
        set(value) = definedExternally
    public var value: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCErrorEventInit : EventInit {
    public var error: RTCError
}

public external interface RTCErrorInit : JsAny {
    public var errorDetail: String /* "data-channel-failure" | "dtls-failure" | "fingerprint-failure" | "hardware-encoder-error" | "hardware-encoder-not-available" | "idp-bad-script-failure" | "idp-execution-failure" | "idp-load-failure" | "idp-need-login" | "idp-timeout" | "idp-tls-failure" | "idp-token-expired" | "idp-token-invalid" | "sctp-failure" | "sdp-syntax-error" */
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
    public var foundation: String?
        get() = definedExternally
        set(value) = definedExternally
    public var ip: String?
        get() = definedExternally
        set(value) = definedExternally
    public var msMTurnSessionId: String?
        get() = definedExternally
        set(value) = definedExternally
    public var port: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var priority: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var protocol: String? /* "tcp" | "udp" */
        get() = definedExternally
        set(value) = definedExternally
    public var relatedAddress: String?
        get() = definedExternally
        set(value) = definedExternally
    public var relatedPort: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var tcpType: String? /* "active" | "passive" | "so" */
        get() = definedExternally
        set(value) = definedExternally
    public var type: String? /* "host" | "prflx" | "relay" | "srflx" */
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCIceCandidateInit : JsAny {
    public var candidate: String?
        get() = definedExternally
        set(value) = definedExternally
    public var sdpMLineIndex: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var sdpMid: String?
        get() = definedExternally
        set(value) = definedExternally
    public var usernameFragment: String?
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
    public var password: String?
        get() = definedExternally
        set(value) = definedExternally
    public var usernameFragment: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCIceServer : JsAny {
    public var urls: JsAny? /* String | JsArray<String> */
        get() = definedExternally
        set(value) = definedExternally
    public var username: JsString?
        get() = definedExternally
        set(value) = definedExternally
    public var credential: JsAny? /* String? | RTCOAuthCredential? */
        get() = definedExternally
        set(value) = definedExternally
    public var credentialType: JsString? /* "oauth" | "password" */
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCIdentityProviderOptions : JsAny {
    public var peerIdentity: String?
        get() = definedExternally
        set(value) = definedExternally
    public var protocol: String?
        get() = definedExternally
        set(value) = definedExternally
    public var usernameHint: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCOAuthCredential : JsAny {
    public var accessToken: String
    public var macKey: String
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
    public var hostCandidate: String?
        get() = definedExternally
        set(value) = definedExternally
    public var statusText: String?
        get() = definedExternally
        set(value) = definedExternally
    public var url: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCPeerConnectionIceEventInit : EventInit {
    public var candidate: RTCIceCandidate?
        get() = definedExternally
        set(value) = definedExternally
    public var url: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtcpParameters : JsAny {
    public var cname: String?
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
    public var mimeType: String
    public var channels: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var clockRate: JsNumber
    public var sdpFmtpLine: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpCodecParameters : JsAny {
    public var mimeType: String
    public var channels: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
    public var sdpFmtpLine: String?
        get() = definedExternally
        set(value) = definedExternally
    public var clockRate: JsNumber
    public var payloadType: JsNumber
}

public external interface RTCRtpCodingParameters : JsAny {
    public var rid: String?
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
    public var dtx: String? /* "disabled" | "enabled" */
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
    public var mechanism: String?
        get() = definedExternally
        set(value) = definedExternally
    public var ssrc: JsNumber?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpHeaderExtensionCapability : JsAny {
    public var uri: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpHeaderExtensionParameters : JsAny {
    public var encrypted: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var id: JsNumber
    public var uri: String
}

public external interface RTCRtpParameters : JsAny {
    public var transactionId: String
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
    public var degradationPreference: String? /* "balanced" | "maintain-framerate" | "maintain-resolution" */
        get() = definedExternally
        set(value) = definedExternally
    public var encodings: JsArray<RTCRtpEncodingParameters>
    public var priority: String? /* "high" | "low" | "medium" | "very-low" */
        get() = definedExternally
        set(value) = definedExternally
    public override var transactionId: String
}

public external interface RTCRtpSynchronizationSource : RTCRtpContributingSource {
    public override var voiceActivityFlag: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpTransceiverInit : JsAny {
    public var direction: String? /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */
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
    public var sdp: String?
        get() = definedExternally
        set(value) = definedExternally
    public var type: String? /* "answer" | "offer" | "pranswer" | "rollback" */
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCStatsReport : ReadonlyMap<JsString> {
    public fun forEach(
        callbackfn: (value: JsAny, key: String, parent: RTCStatsReport) -> JsUndefined,
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
    public fun getAlgorithm(): String
    public fun getFingerprints(): JsArray<RTCDtlsFingerprint>
}

public external class RTCDTMFSender : EventTarget {
    public var canInsertDTMF: Boolean
    public var ontonechange: ((self: RTCDTMFSender, ev: RTCDTMFToneChangeEvent) -> JsAny)?
    public var toneBuffer: String
    public fun insertDTMF(tones: String, duration: JsNumber = definedExternally, interToneGap: JsNumber = definedExternally)
}

public external class RTCDTMFToneChangeEvent : Event {
    public var tone: String
}

public external class RTCDataChannel : EventTarget {
    public var label: String
    public var ordered: Boolean
    public var maxPacketLifeTime: JsNumber?
    public var maxRetransmits: JsNumber?
    public var protocol: String
    public var negotiated: Boolean
    public var id: JsNumber?
    public var readyState: String /* "closed" | "closing" | "connecting" | "open" */
    public var bufferedAmount: JsNumber
    public var bufferedAmountLowThreshold: JsNumber
    public fun close()
    public fun send(data: String)
    public fun send(data: Blob)
    public fun send(data: ArrayBuffer)
    public fun send(data: ArrayBufferView)
    public var onopen: ((self: RTCDataChannel, ev: Event) -> JsAny)?
    public var onmessage: ((self: RTCDataChannel, ev: MessageEvent) -> JsAny)?
    public var onbufferedamountlow: ((self: RTCDataChannel, ev: Event) -> JsAny)?
    public var onclose: ((self: RTCDataChannel, ev: Event) -> JsAny)?
    public var binaryType: String
    public var onerror: ((self: RTCDataChannel, ev: RTCErrorEvent) -> JsAny)?
    public var priority: String /* "high" | "low" | "medium" | "very-low" */
}

public external class RTCDataChannelEvent : Event {
    public var channel: RTCDataChannel
}

public external class RTCDtlsTransport : EventTarget {
    public var iceTransport: RTCIceTransport
    public var state: String /* "closed" | "connected" | "connecting" | "failed" | "new" */
    public fun getRemoteCertificates(): JsArray<ArrayBuffer>
    public var onerror: ((self: RTCDtlsTransport, ev: RTCErrorEvent) -> JsAny)?
    public var onstatechange: ((self: RTCDtlsTransport, ev: Event) -> JsAny)?
}

public external class RTCDtlsTransportStateChangedEvent : Event {
    public var state: String /* "closed" | "connected" | "connecting" | "failed" | "new" */
}

public external class RTCError : DOMException {
    public var errorDetail: String /* "data-channel-failure" | "dtls-failure" | "fingerprint-failure" | "hardware-encoder-error" | "hardware-encoder-not-available" | "idp-bad-script-failure" | "idp-execution-failure" | "idp-load-failure" | "idp-need-login" | "idp-timeout" | "idp-tls-failure" | "idp-token-expired" | "idp-token-invalid" | "sctp-failure" | "sdp-syntax-error" */
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
    public var candidate: String
    public var component: String /* "rtcp" | "rtp" */
    public var foundation: String?
    public var port: JsNumber?
    public var priority: JsNumber?
    public var protocol: String /* "tcp" | "udp" */
    public var relatedAddress: String?
    public var relatedPort: JsNumber?
    public var sdpMLineIndex: JsNumber?
    public var sdpMid: String?
    public var tcpType: String /* "active" | "passive" | "so" */
    public var type: String /* "host" | "prflx" | "relay" | "srflx" */
    public var usernameFragment: String?
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
    public var role: String /* "controlled" | "controlling" | "unknown" */
    public var gatheringState: String /* "complete" | "gathering" | "new" */
    public fun getLocalCandidates(): JsArray<RTCIceCandidate>
    public fun getRemoteCandidates(): JsArray<RTCIceCandidate>
    public fun getLocalParameters(): RTCIceParameters?
    public fun getRemoteParameters(): RTCIceParameters?
    public fun getSelectedCandidatePair(): RTCIceCandidatePair?
    public var onstatechange: ((self: RTCIceTransport, ev: Event) -> JsAny)?
    public var ongatheringstatechange: ((self: RTCIceTransport, ev: Event) -> JsAny)?
    public var onselectedcandidatepairchange: ((self: RTCIceTransport, ev: Event) -> JsAny)?
    public var component: String /* "rtcp" | "rtp" */
    public var state: String /* "checking" | "closed" | "completed" | "connected" | "disconnected" | "failed" | "new" */
}

public external class RTCIceTransportStateChangedEvent : Event {
    public var state: String /* "checking" | "closed" | "completed" | "connected" | "disconnected" | "failed" | "new" */
}

public external class RTCIdentityAssertion : JsAny {
    public var idp: String
    public var name: String
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
    public var signalingState: String /* "closed" | "have-local-offer" | "have-local-pranswer" | "have-remote-offer" | "have-remote-pranswer" | "stable" */
    public var connectionState: String /* "closed" | "connected" | "connecting" | "disconnected" | "failed" | "new" */
    public fun getConfiguration(): RTCConfiguration
    public fun setConfiguration(configuration: RTCConfiguration)
    public fun close()
    public var onicecandidateerror: ((self: RTCPeerConnection, ev: RTCPeerConnectionIceErrorEvent) -> JsAny)?
    public var onconnectionstatechange: ((self: RTCPeerConnection, ev: Event) -> JsAny)?
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
    public fun addTransceiver(trackOrKind: String, init: RTCRtpTransceiverInit = definedExternally): RTCRtpTransceiver
    public fun addTransceiver(trackOrKind: String): RTCRtpTransceiver
    public var ontrack: ((self: RTCPeerConnection, ev: RTCTrackEvent) -> JsAny)?
    public var sctp: RTCSctpTransport?
    public fun createDataChannel(
        label: String?,
        dataChannelDict: RTCDataChannelInit = definedExternally
    ): RTCDataChannel

    public fun createDataChannel(label: String?): RTCDataChannel
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
    public var iceConnectionState: String /* "checking" | "closed" | "completed" | "connected" | "disconnected" | "failed" | "new" */
    public var iceGatheringState: String /* "complete" | "gathering" | "new" */
    public var idpErrorInfo: String?
    public var idpLoginUrl: String?
    public var onicecandidate: ((self: RTCPeerConnection, ev: RTCPeerConnectionIceEvent) -> JsAny?)?
    public var oniceconnectionstatechange: ((self: RTCPeerConnection, ev: Event) -> JsAny)?
    public var onicegatheringstatechange: ((self: RTCPeerConnection, ev: Event) -> JsAny)?
    public var onnegotiationneeded: ((self: RTCPeerConnection, ev: Event) -> JsAny)?
    public var onsignalingstatechange: ((self: RTCPeerConnection, ev: Event) -> JsAny)?
    public var onstatsended: ((self: RTCPeerConnection, ev: RTCStatsEvent) -> JsAny)?
    public var peerIdentity: Promise<RTCIdentityAssertion>
    public fun createAnswer(options: RTCOfferOptions = definedExternally): Promise<RTCSessionDescriptionInit>
    public fun createDataChannel(label: String, dataChannelDict: RTCDataChannelInit = definedExternally): RTCDataChannel
    public fun createDataChannel(label: String): RTCDataChannel
    public fun getIdentityAssertion(): Promise<JsString>
    public fun setIdentityProvider(provider: String, options: RTCIdentityProviderOptions = definedExternally)
}

public external class RTCPeerConnectionIceErrorEvent : Event {
    public var hostCandidate: String
    public var url: String
    public var errorCode: JsNumber
    public var errorText: String
}

public external class RTCPeerConnectionIceEvent : Event {
    public var url: String?
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
    public var mid: String?
    public var sender: RTCRtpSender
    public var receiver: RTCRtpReceiver
    public var stopped: Boolean
    public var direction: String /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */
    public fun setDirection(direction: String /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */)
    public fun stop()
    public fun setCodecPreferences(codecs: JsArray<RTCRtpCodecCapability>)
    public var currentDirection: String /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */
}

public external class RTCSctpTransport private constructor() : EventTarget {
    public var transport: RTCDtlsTransport
    public var maxMessageSize: JsNumber
    public var maxChannels: JsNumber?
    public var onstatechange: ((self: RTCSctpTransport, ev: Event) -> JsAny)?
    public var state: String /* "closed" | "connected" | "connecting" */
}

public external class RTCSessionDescription {
    public var sdp: String
    public var type: String /* "answer" | "offer" | "pranswer" | "rollback" */
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
    public fun generateCertificate(keygenAlgorithm: String): Promise<RTCCertificate>
}

public typealias RTCPeerConnectionErrorCallback = (error: DOMException) -> Unit

public typealias RTCSessionDescriptionCallback = (description: RTCSessionDescriptionInit) -> Unit
