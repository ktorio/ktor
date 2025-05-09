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

public external interface RTCConfiguration {
    public var iceServers: Array<RTCIceServer>?
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
    public var certificates: Array<RTCCertificate>?
        get() = definedExternally
        set(value) = definedExternally
    public var iceCandidatePoolSize: Number?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCDTMFToneChangeEventInit : EventInit {
    public var tone: String
}

public external interface RTCDataChannelEventInit : EventInit {
    public var channel: RTCDataChannel
}

public external interface RTCDataChannelInit {
    public var ordered: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var maxPacketLifeTime: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var maxRetransmits: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var protocol: String?
        get() = definedExternally
        set(value) = definedExternally
    public var negotiated: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var id: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var priority: String? /* "high" | "low" | "medium" | "very-low" */
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCDtlsFingerprint {
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

public external interface RTCErrorInit {
    public var errorDetail: String /* "data-channel-failure" | "dtls-failure" | "fingerprint-failure" | "hardware-encoder-error" | "hardware-encoder-not-available" | "idp-bad-script-failure" | "idp-execution-failure" | "idp-load-failure" | "idp-need-login" | "idp-timeout" | "idp-tls-failure" | "idp-token-expired" | "idp-token-invalid" | "sctp-failure" | "sdp-syntax-error" */
    public var httpRequestStatusCode: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var receivedAlert: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var sctpCauseCode: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var sdpLineNumber: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var sentAlert: Number?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCIceCandidateComplete

public external interface RTCIceCandidateDictionary {
    public var foundation: String?
        get() = definedExternally
        set(value) = definedExternally
    public var ip: String?
        get() = definedExternally
        set(value) = definedExternally
    public var msMTurnSessionId: String?
        get() = definedExternally
        set(value) = definedExternally
    public var port: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var priority: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var protocol: String? /* "tcp" | "udp" */
        get() = definedExternally
        set(value) = definedExternally
    public var relatedAddress: String?
        get() = definedExternally
        set(value) = definedExternally
    public var relatedPort: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var tcpType: String? /* "active" | "passive" | "so" */
        get() = definedExternally
        set(value) = definedExternally
    public var type: String? /* "host" | "prflx" | "relay" | "srflx" */
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCIceCandidateInit {
    public var candidate: String?
        get() = definedExternally
        set(value) = definedExternally
    public var sdpMLineIndex: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var sdpMid: String?
        get() = definedExternally
        set(value) = definedExternally
    public var usernameFragment: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCIceCandidatePair {
    public var local: RTCIceCandidate?
        get() = definedExternally
        set(value) = definedExternally
    public var remote: RTCIceCandidate?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCIceParameters {
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

public external interface RTCIceServer {
    public var urls: dynamic /* String | Array<String> */
        get() = definedExternally
        set(value) = definedExternally
    public var username: String?
        get() = definedExternally
        set(value) = definedExternally
    public var credential: dynamic /* String? | RTCOAuthCredential? */
        get() = definedExternally
        set(value) = definedExternally
    public var credentialType: String? /* "oauth" | "password" */
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCIdentityProviderOptions {
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

public external interface RTCOAuthCredential {
    public var accessToken: String
    public var macKey: String
}

public external interface RTCOfferAnswerOptions {
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
    public var errorCode: Number
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

public external interface RTCRtcpParameters {
    public var cname: String?
        get() = definedExternally
        set(value) = definedExternally
    public var reducedSize: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpCapabilities {
    public var codecs: Array<RTCRtpCodecCapability>
    public var headerExtensions: Array<RTCRtpHeaderExtensionCapability>
}

public external interface RTCRtpCodecCapability {
    public var mimeType: String
    public var channels: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var clockRate: Number
    public var sdpFmtpLine: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpCodecParameters {
    public var mimeType: String
    public var channels: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var sdpFmtpLine: String?
        get() = definedExternally
        set(value) = definedExternally
    public var clockRate: Number
    public var payloadType: Number
}

public external interface RTCRtpCodingParameters {
    public var rid: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpContributingSource {
    public var source: Number
    public var voiceActivityFlag: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var audioLevel: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var rtpTimestamp: Number
    public var timestamp: Number
}

public external interface RTCRtpDecodingParameters : RTCRtpCodingParameters

public external interface RTCRtpEncodingParameters : RTCRtpCodingParameters {
    public var scaleResolutionDownBy: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var active: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var codecPayloadType: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var dtx: String? /* "disabled" | "enabled" */
        get() = definedExternally
        set(value) = definedExternally
    public var maxBitrate: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var maxFramerate: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var ptime: Number?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpFecParameters {
    public var mechanism: String?
        get() = definedExternally
        set(value) = definedExternally
    public var ssrc: Number?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpHeaderExtensionCapability {
    public var uri: String?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpHeaderExtensionParameters {
    public var encrypted: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var id: Number
    public var uri: String
}

public external interface RTCRtpParameters {
    public var transactionId: String
    public var codecs: Array<RTCRtpCodecParameters>
    public var headerExtensions: Array<RTCRtpHeaderExtensionParameters>
    public var rtcp: RTCRtcpParameters
}

public external interface RTCRtpReceiveParameters : RTCRtpParameters {
    public var encodings: Array<RTCRtpDecodingParameters>
}

public external interface RTCRtpRtxParameters {
    public var ssrc: Number?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCRtpSendParameters : RTCRtpParameters {
    public var degradationPreference: String? /* "balanced" | "maintain-framerate" | "maintain-resolution" */
        get() = definedExternally
        set(value) = definedExternally
    public var encodings: Array<RTCRtpEncodingParameters>
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

public external interface RTCRtpTransceiverInit {
    public var direction: String? /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */
        get() = definedExternally
        set(value) = definedExternally
    public var streams: Array<MediaStream>?
        get() = definedExternally
        set(value) = definedExternally
    public var sendEncodings: Array<RTCRtpEncodingParameters>?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCSessionDescriptionInit {
    public var sdp: String?
        get() = definedExternally
        set(value) = definedExternally
    public var type: String? /* "answer" | "offer" | "pranswer" | "rollback" */
        get() = definedExternally
        set(value) = definedExternally
}

public external interface RTCStatsReport : ReadonlyMap<String> {
    public fun forEach(
        callbackfn: (value: Any, key: String, parent: RTCStatsReport) -> Unit,
        thisArg: Any = definedExternally
    )
}

public external interface RTCStats {
    public val timestamp: Number
    public val type: String
    public val id: String
}

public external interface RTCMediaStats : RTCStats {
    public val kind: String
    public val trackId: String
}

public external interface RTCAudioStats : RTCMediaStats {
    public val audioLevel: Number?
    public val totalAudioEnergy: Number?
    public val totalSamplesDuration: Number?
}

public external interface RTCVideoStats : RTCMediaStats {
    public val width: Number?
    public val height: Number?
    public val frames: Number?
    public val framesPerSecond: Number?
}

public external interface RTCTrackEventInit : EventInit {
    public var receiver: RTCRtpReceiver
    public var streams: Array<MediaStream>?
        get() = definedExternally
        set(value) = definedExternally
    public var track: MediaStreamTrack
    public var transceiver: RTCRtpTransceiver
}

public external interface RTCCertificate {
    public var expires: Number
    public fun getAlgorithm(): String
    public fun getFingerprints(): Array<RTCDtlsFingerprint>
}

public external class RTCDTMFSender : EventTarget {
    public var canInsertDTMF: Boolean
    public var ontonechange: ((self: RTCDTMFSender, ev: RTCDTMFToneChangeEvent) -> Any)?
    public var toneBuffer: String
    public fun insertDTMF(tones: String, duration: Number = definedExternally, interToneGap: Number = definedExternally)
}

public external class RTCDTMFToneChangeEvent : Event {
    public var tone: String
}

public external class RTCDataChannel : EventTarget {
    public var label: String
    public var ordered: Boolean
    public var maxPacketLifeTime: Number?
    public var maxRetransmits: Number?
    public var protocol: String
    public var negotiated: Boolean
    public var id: Number?
    public var readyState: String /* "closed" | "closing" | "connecting" | "open" */
    public var bufferedAmount: Number
    public var bufferedAmountLowThreshold: Number
    public fun close()
    public fun send(data: String)
    public fun send(data: Blob)
    public fun send(data: ArrayBuffer)
    public fun send(data: ArrayBufferView)
    public var onopen: ((self: RTCDataChannel, ev: Event) -> Any)?
    public var onmessage: ((self: RTCDataChannel, ev: MessageEvent) -> Any)?
    public var onbufferedamountlow: ((self: RTCDataChannel, ev: Event) -> Any)?
    public var onclose: ((self: RTCDataChannel, ev: Event) -> Any)?
    public var binaryType: String
    public var onerror: ((self: RTCDataChannel, ev: RTCErrorEvent) -> Any)?
    public var priority: String /* "high" | "low" | "medium" | "very-low" */
}

public external class RTCDataChannelEvent : Event {
    public var channel: RTCDataChannel
}

public external class RTCDtlsTransport : EventTarget {
    public var iceTransport: RTCIceTransport
    public var state: String /* "closed" | "connected" | "connecting" | "failed" | "new" */
    public fun getRemoteCertificates(): Array<ArrayBuffer>
    public var onerror: ((self: RTCDtlsTransport, ev: RTCErrorEvent) -> Any)?
    public var onstatechange: ((self: RTCDtlsTransport, ev: Event) -> Any)?
}

public external class RTCDtlsTransportStateChangedEvent : Event {
    public var state: String /* "closed" | "connected" | "connecting" | "failed" | "new" */
}

public external class RTCError : DOMException {
    public var errorDetail: String /* "data-channel-failure" | "dtls-failure" | "fingerprint-failure" | "hardware-encoder-error" | "hardware-encoder-not-available" | "idp-bad-script-failure" | "idp-execution-failure" | "idp-load-failure" | "idp-need-login" | "idp-timeout" | "idp-tls-failure" | "idp-token-expired" | "idp-token-invalid" | "sctp-failure" | "sdp-syntax-error" */
    public var httpRequestStatusCode: Number?
    public var receivedAlert: Number?
    public var sctpCauseCode: Number?
    public var sdpLineNumber: Number?
    public var sentAlert: Number?
}

public external class RTCErrorEvent : Event {
    public var error: RTCError
}

public external class RTCIceCandidate {
    public constructor(init: RTCIceCandidateInit)

    public var candidate: String
    public var component: String /* "rtcp" | "rtp" */
    public var foundation: String?
    public var port: Number?
    public var priority: Number?
    public var protocol: String /* "tcp" | "udp" */
    public var relatedAddress: String?
    public var relatedPort: Number?
    public var sdpMLineIndex: Number?
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
    public var candidate: dynamic /* RTCIceCandidateDictionary | RTCIceCandidateComplete */
        get() = definedExternally
        set(value) = definedExternally
}

public external class RTCIceTransport : EventTarget {
    public var role: String /* "controlled" | "controlling" | "unknown" */
    public var gatheringState: String /* "complete" | "gathering" | "new" */
    public fun getLocalCandidates(): Array<RTCIceCandidate>
    public fun getRemoteCandidates(): Array<RTCIceCandidate>
    public fun getLocalParameters(): RTCIceParameters?
    public fun getRemoteParameters(): RTCIceParameters?
    public fun getSelectedCandidatePair(): RTCIceCandidatePair?
    public var onstatechange: ((self: RTCIceTransport, ev: Event) -> Any)?
    public var ongatheringstatechange: ((self: RTCIceTransport, ev: Event) -> Any)?
    public var onselectedcandidatepairchange: ((self: RTCIceTransport, ev: Event) -> Any)?
    public var component: String /* "rtcp" | "rtp" */
    public var state: String /* "checking" | "closed" | "completed" | "connected" | "disconnected" | "failed" | "new" */
}

public external class RTCIceTransportStateChangedEvent : Event {
    public var state: String /* "checking" | "closed" | "completed" | "connected" | "disconnected" | "failed" | "new" */
}

public external class RTCIdentityAssertion {
    public var idp: String
    public var name: String
}

public external class RTCPeerConnection(config: RTCConfiguration) : EventTarget {
    public fun createOffer(options: RTCOfferOptions = definedExternally): Promise<RTCSessionDescriptionInit>
    public fun createOffer(): Promise<RTCSessionDescriptionInit>
    public fun createAnswer(options: RTCAnswerOptions = definedExternally): Promise<RTCSessionDescriptionInit>
    public fun createAnswer(): Promise<RTCSessionDescriptionInit>
    public fun setLocalDescription(description: RTCSessionDescription): Promise<Unit>
    public var localDescription: RTCSessionDescription?
    public var currentLocalDescription: RTCSessionDescription?
    public var pendingLocalDescription: RTCSessionDescription?
    public fun setRemoteDescription(description: RTCSessionDescription): Promise<Unit>
    public var remoteDescription: RTCSessionDescription?
    public var currentRemoteDescription: RTCSessionDescription?
    public var pendingRemoteDescription: RTCSessionDescription?
    public fun addIceCandidate(candidate: RTCIceCandidateInit = definedExternally): Promise<Unit>
    public fun addIceCandidate(): Promise<Unit>
    public fun addIceCandidate(candidate: RTCIceCandidate = definedExternally): Promise<Unit>
    public var signalingState: String /* "closed" | "have-local-offer" | "have-local-pranswer" | "have-remote-offer" | "have-remote-pranswer" | "stable" */
    public var connectionState: String /* "closed" | "connected" | "connecting" | "disconnected" | "failed" | "new" */
    public fun getConfiguration(): RTCConfiguration
    public fun setConfiguration(configuration: RTCConfiguration)
    public fun close()
    public var onicecandidateerror: ((ev: RTCPeerConnectionIceErrorEvent) -> Unit)?
    public var onconnectionstatechange: ((ev: Event) -> Unit)?
    public fun getSenders(): Array<RTCRtpSender>
    public fun getReceivers(): Array<RTCRtpReceiver>
    public fun getTransceivers(): Array<RTCRtpTransceiver>
    public fun addTrack(track: MediaStreamTrack, vararg streams: MediaStream): RTCRtpSender
    public fun removeTrack(sender: RTCRtpSender)
    public fun addTransceiver(
        trackOrKind: MediaStreamTrack,
        init: RTCRtpTransceiverInit = definedExternally
    ): RTCRtpTransceiver

    public fun addTransceiver(trackOrKind: MediaStreamTrack): RTCRtpTransceiver
    public fun addTransceiver(trackOrKind: String, init: RTCRtpTransceiverInit = definedExternally): RTCRtpTransceiver
    public fun addTransceiver(trackOrKind: String): RTCRtpTransceiver
    public var ontrack: ((self: RTCPeerConnection, ev: RTCTrackEvent) -> Any)?
    public var sctp: RTCSctpTransport?
    public fun createDataChannel(
        label: String?,
        dataChannelDict: RTCDataChannelInit = definedExternally
    ): RTCDataChannel

    public fun createDataChannel(label: String?): RTCDataChannel
    public var ondatachannel: ((self: RTCPeerConnection, ev: RTCDataChannelEvent) -> Any)?
    public fun getStats(selector: MediaStreamTrack? = definedExternally): Promise<RTCStatsReport>
    public fun getStats(): Promise<RTCStatsReport>
    public fun createOffer(
        successCallback: RTCSessionDescriptionCallback,
        failureCallback: RTCPeerConnectionErrorCallback,
        options: RTCOfferOptions = definedExternally
    ): Promise<Unit>

    public fun createOffer(
        successCallback: RTCSessionDescriptionCallback,
        failureCallback: RTCPeerConnectionErrorCallback
    ): Promise<Unit>

    public fun setLocalDescription(
        description: RTCSessionDescriptionInit,
        successCallback: () -> Unit,
        failureCallback: RTCPeerConnectionErrorCallback
    ): Promise<Unit>

    public fun createAnswer(
        successCallback: RTCSessionDescriptionCallback,
        failureCallback: RTCPeerConnectionErrorCallback
    ): Promise<Unit>

    public fun setRemoteDescription(
        description: RTCSessionDescriptionInit,
        successCallback: () -> Unit,
        failureCallback: RTCPeerConnectionErrorCallback
    ): Promise<Unit>

    public fun addIceCandidate(
        candidate: RTCIceCandidateInit,
        successCallback: () -> Unit,
        failureCallback: RTCPeerConnectionErrorCallback
    ): Promise<Unit>

    public fun addIceCandidate(
        candidate: RTCIceCandidate,
        successCallback: () -> Unit,
        failureCallback: RTCPeerConnectionErrorCallback
    ): Promise<Unit>

    public fun getStats(
        selector: MediaStreamTrack?,
        successCallback: (report: RTCStatsReport) -> Unit,
        failureCallback: RTCPeerConnectionErrorCallback
    ): Promise<Unit>

    public var canTrickleIceCandidates: Boolean?
    public var iceConnectionState: String /* "checking" | "closed" | "completed" | "connected" | "disconnected" | "failed" | "new" */
    public var iceGatheringState: String /* "complete" | "gathering" | "new" */
    public var idpErrorInfo: String?
    public var idpLoginUrl: String?
    public var onicecandidate: ((ev: RTCPeerConnectionIceEvent) -> Unit)?
    public var oniceconnectionstatechange: ((ev: Event) -> Any)?
    public var onicegatheringstatechange: ((ev: Event) -> Any)?
    public var onnegotiationneeded: ((ev: Event) -> Any)?
    public var onsignalingstatechange: ((ev: Event) -> Any)?
    public var onstatsended: ((ev: RTCStatsEvent) -> Any)?
    public var peerIdentity: Promise<RTCIdentityAssertion>
    public fun createAnswer(options: RTCOfferOptions = definedExternally): Promise<RTCSessionDescriptionInit>
    public fun createDataChannel(label: String, dataChannelDict: RTCDataChannelInit = definedExternally): RTCDataChannel
    public fun createDataChannel(label: String): RTCDataChannel
    public fun getIdentityAssertion(): Promise<String>
    public fun setIdentityProvider(provider: String, options: RTCIdentityProviderOptions = definedExternally)
}

public external class RTCPeerConnectionIceErrorEvent : Event {
    public var hostCandidate: String
    public var url: String
    public var errorCode: Number
    public var errorText: String
}

public external class RTCPeerConnectionIceEvent : Event {
    public var url: String?
    public var candidate: RTCIceCandidate?
}

public external interface RTCRtpReceiver {
    public fun getParameters(): dynamic /* RTCRtpParameters | RTCRtpReceiveParameters */
    public fun getContributingSources(): Array<RTCRtpContributingSource>
    public var rtcpTransport: RTCDtlsTransport?
    public var track: MediaStreamTrack
    public var transport: RTCDtlsTransport?
    public fun getStats(): Promise<RTCStatsReport>
    public fun getSynchronizationSources(): Array<RTCRtpSynchronizationSource>
}

public external interface RTCRtpSender {
    public fun setParameters(parameters: RTCRtpParameters = definedExternally): Promise<Unit>
    public fun setParameters(): Promise<dynamic>
    public fun getParameters(): dynamic /* RTCRtpParameters | RTCRtpSendParameters */
    public fun replaceTrack(withTrack: MediaStreamTrack): Promise<Unit>
    public var dtmf: RTCDTMFSender?
    public var rtcpTransport: RTCDtlsTransport?
    public var track: MediaStreamTrack?
    public var transport: RTCDtlsTransport?
    public fun getStats(): Promise<RTCStatsReport>
    public fun replaceTrack(withTrack: MediaStreamTrack?): Promise<Unit>
    public fun setParameters(parameters: RTCRtpSendParameters): Promise<Unit>
    public fun setStreams(vararg streams: MediaStream)
}

public external interface RTCRtpTransceiver {
    public var mid: String?
    public var sender: RTCRtpSender
    public var receiver: RTCRtpReceiver
    public var stopped: Boolean
    public var direction: String /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */
    public fun setDirection(direction: String /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */)
    public fun stop()
    public fun setCodecPreferences(codecs: Array<RTCRtpCodecCapability>)
    public fun setCodecPreferences(codecs: Iterable<RTCRtpCodecCapability>)
    public var currentDirection: String /* "inactive" | "recvonly" | "sendonly" | "sendrecv" | "stopped" */
}

public external class RTCSctpTransport private constructor() : EventTarget {
    public var transport: RTCDtlsTransport
    public var maxMessageSize: Number
    public var maxChannels: Number?
    public var onstatechange: ((self: RTCSctpTransport, ev: Event) -> Any)?
    public var state: String /* "closed" | "connected" | "connecting" */
}

public external class RTCSessionDescription {
    public constructor(description: RTCSessionDescriptionInit)

    public var sdp: String
    public var type: String /* "answer" | "offer" | "pranswer" | "rollback" */
    public fun toJSON(): Any
}

public external class RTCSsrcConflictEvent : Event {
    public var ssrc: Number
}

public external class RTCStatsEvent : Event {
    public var report: RTCStatsReport
}

public external class RTCTrackEvent : Event {
    public var receiver: RTCRtpReceiver
    public var track: MediaStreamTrack
    public var streams: Array<MediaStream>
    public var transceiver: RTCRtpTransceiver
}

public external interface RTCPeerConnectionStatic {
    public var defaultIceServers: Array<RTCIceServer>
    public fun generateCertificate(keygenAlgorithm: String): Promise<RTCCertificate>
}

public typealias RTCPeerConnectionErrorCallback = (error: DOMException) -> Unit

public typealias RTCSessionDescriptionCallback = (description: RTCSessionDescriptionInit) -> Unit
