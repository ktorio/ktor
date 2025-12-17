/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.utils.io.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Abstract class representing a peer-to-peer connection.
 *
 * @see [MDN RTCPeerConnection](https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection)
 */
public abstract class WebRtcPeerConnection private constructor(
    protected val events: WebRtcConnectionEventsEmitter,
    protected val coroutineScope: CoroutineScope
) : Closeable, WebRtcConnectionEvents by events {

    /**
     * @param coroutineContext Coroutine context to fetch statistics and emit events.
     * @param config Configuration for the peer connection.
     */
    public constructor(
        coroutineContext: CoroutineContext,
        config: WebRtcConnectionConfig
    ) : this(events = WebRtcConnectionEventsEmitter(config), coroutineScope = CoroutineScope(coroutineContext)) {
        // Start fetching statistics
        val refreshRate = config.statsRefreshRate
        if (refreshRate != null) {
            coroutineScope.launch {
                while (isActive) {
                    delay(duration = refreshRate)
                    events.emitStats(stats = getStatistics())
                }
            }
        }
    }

    /**
     * The local session description for this connection.
     */
    public abstract val localDescription: WebRtc.SessionDescription?

    /**
     * The remote session description for this connection.
     */
    public abstract val remoteDescription: WebRtc.SessionDescription?

    /**
     * Creates an SDP offer for establishing a connection.
     *
     * @return The session description representing the offer.
     */
    public abstract suspend fun createOffer(): WebRtc.SessionDescription

    /**
     * Creates an SDP answer in response to a received offer.
     *
     * @return The session description representing the answer.
     */
    public abstract suspend fun createAnswer(): WebRtc.SessionDescription

    public abstract suspend fun createDataChannel(
        label: String,
        options: (WebRtcDataChannelOptions.() -> Unit) = {}
    ): WebRtcDataChannel

    /**
     * Sets the local session description.
     */
    public abstract suspend fun setLocalDescription(description: WebRtc.SessionDescription)

    /**
     * Sets the remote session description.
     */
    public abstract suspend fun setRemoteDescription(description: WebRtc.SessionDescription)

    /**
     * Adds a remote ICE candidate to this connection.
     */
    public abstract suspend fun addIceCandidate(candidate: WebRtc.IceCandidate)

    /**
     * Adds a media track to this connection.
     *
     * @param track The media track to add.
     * @return An RTP sender for the added track.
     */
    public abstract suspend fun addTrack(track: WebRtcMedia.Track): WebRtc.RtpSender

    /**
     * Removes a track from this connection using its RTP sender.
     *
     * @param sender The RTP sender for the track to remove.
     */
    public abstract suspend fun removeTrack(sender: WebRtc.RtpSender)

    /**
     * Removes a track from this connection.
     *
     * @param track The media track to remove.
     */
    public abstract suspend fun removeTrack(track: WebRtcMedia.Track)

    /**
     * Restarts ICE negotiation for this connection.
     * Should emit `negotiationNeeded` event.
     * The next offer will be created with `iceRestart` option.
     */
    public abstract fun restartIce()

    /**
     * Returns data providing statistics about the overall connection.
     */
    public abstract suspend fun getStatistics(): List<WebRtc.Stats>

    public suspend fun awaitIceGatheringComplete() {
        iceGatheringState.first { it == WebRtc.IceGatheringState.COMPLETE }
    }

    /**
     * Runs a [block] in the coroutine scope of the peer connection without extra dispatching.
     * This should be used to run some background tasks without losing thrown exceptions.
     */
    internal inline fun runInConnectionScope(crossinline block: () -> Unit) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { block() }
    }

    override fun close() {
        coroutineScope.cancel()
    }
}
