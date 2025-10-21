/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.rs

import io.ktor.client.webrtc.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import uniffi.ktor_client_webrtc.*
import kotlin.coroutines.CoroutineContext

public class RustWebRtcConnection(
    internal val inner: PeerConnection,
    coroutineContext: CoroutineContext,
    config: WebRtcConnectionConfig
) : WebRtcPeerConnection(coroutineContext, config) {

    init {
        inner.registerObserver(object : PeerConnectionObserver {
            override fun onConnectionStateChange(state: ConnectionState) = runInConnectionScope {
                events.emitConnectionStateChange(state.toKtor())
            }

            override fun onIceConnectionStateChange(state: IceConnectionState) = runInConnectionScope {
                events.emitIceConnectionStateChange(state.toKtor())
            }

            override fun onIceGatheringStateChange(state: IceGatheringState) = runInConnectionScope {
                events.emitIceGatheringStateChange(state.toKtor())
            }

            override fun onSignalingStateChange(state: SignalingState) = runInConnectionScope {
                events.emitSignalingStateChange(state.toKtor())
            }

            override fun onIceCandidate(candidate: IceCandidate) = runInConnectionScope {
                events.emitIceCandidate(candidate.toKtor())
            }

            override fun onDataChannel(channel: DataChannel) = runInConnectionScope {
                val channel = RustWebRtcDataChannel(
                    inner = channel,
                    coroutineScope = coroutineScope,
                    receiveOptions = DataChannelReceiveOptions()
                )
                channel.setupEvents(events)
            }

            override fun onTrack(track: MediaStreamTrack) = runInConnectionScope {
                val mediaTrack = RustMediaTrack.from(nativeTrack = track, coroutineScope)
                events.emitAddTrack(mediaTrack)
            }

            override fun onRemoveTrack(track: MediaStreamTrack) = runInConnectionScope {
                val mediaTrack = RustMediaTrack.from(nativeTrack = track, coroutineScope)
                events.emitRemoveTrack(mediaTrack)
            }

            override fun onNegotiationNeeded() = runInConnectionScope {
                events.emitNegotiationNeeded()
            }

            override fun onError(error: RtcException) = runInConnectionScope {
                coroutineScope.cancel("Peer connection error: ${error.message}")
            }
        })
    }

    // There is no need to make those getters async
    // It is async in Rust because of async mutex
    override val localDescription: WebRtc.SessionDescription?
        get() = runBlocking { inner.getLocalDescription()?.toKtor() }

    override val remoteDescription: WebRtc.SessionDescription?
        get() = runBlocking { inner.getRemoteDescription()?.toKtor() }

    override suspend fun createOffer(): WebRtc.SessionDescription {
        return inner.createOffer().toKtor()
    }

    override suspend fun createAnswer(): WebRtc.SessionDescription {
        return inner.createAnswer().toKtor()
    }

    override suspend fun createDataChannel(
        label: String,
        options: WebRtcDataChannelOptions.() -> Unit
    ): WebRtcDataChannel {
        val options = WebRtcDataChannelOptions().apply(options)
        val channelInit = DataChannelInit(
            negotiated = when (options.negotiated) {
                true -> options.id?.toUShort() ?: error("Data channel id is required for negotiated channel.")
                false -> null
            },
            ordered = options.ordered,
            protocol = options.protocol,
            maxRetransmits = options.maxRetransmits?.toUShort(),
            maxPacketLifeTime = options.maxPacketLifeTime?.inWholeMilliseconds?.toUShort(),
        )
        val nativeChannel = inner.createDataChannel(label, channelInit)
        val receiveOptions = DataChannelReceiveOptions().apply(options.receiveOptions)
        return RustWebRtcDataChannel(nativeChannel, coroutineScope, receiveOptions).apply {
            setupEvents(events)
        }
    }

    override suspend fun setLocalDescription(description: WebRtc.SessionDescription): Unit = withSdpException {
        inner.setLocalDescription(description.toRust())
    }

    override suspend fun setRemoteDescription(description: WebRtc.SessionDescription): Unit = withSdpException {
        inner.setRemoteDescription(description.toRust())
    }

    override suspend fun addIceCandidate(candidate: WebRtc.IceCandidate): Unit = withIceException {
        inner.addIceCandidate(candidate.toRust())
    }

    override suspend fun addTrack(track: WebRtcMedia.Track): WebRtc.RtpSender {
        val track = track as? RustMediaTrack ?: error("Wrong track implementation.")
        return RustRtpSender(inner.addTrack(track.inner))
    }

    override suspend fun removeTrack(sender: WebRtc.RtpSender) {
        val sender = sender as? RustRtpSender ?: error("Wrong sender implementation.")
        return inner.removeTrackBySender(sender.inner)
    }

    override suspend fun removeTrack(track: WebRtcMedia.Track) {
        val track = track as? RustMediaTrack ?: error("Wrong track implementation.")
        return inner.removeTrack(track.inner)
    }

    override fun restartIce(): Unit = runBlocking {
        inner.restartIce()
    }

    override suspend fun getStatistics(): List<WebRtc.Stats> {
        return inner.getStatistics().map { it.toKtor() }
    }

    override fun close() {
        super.close()
        inner.destroy()
    }
}

/**
 * Returns implementation of the rtp parameters that is used under the hood. Use it with caution.
 */
public fun WebRtcPeerConnection.getNative(): PeerConnection {
    val parameters = this as? RustWebRtcConnection ?: error("Wrong connection implementation.")
    return parameters.inner
}
