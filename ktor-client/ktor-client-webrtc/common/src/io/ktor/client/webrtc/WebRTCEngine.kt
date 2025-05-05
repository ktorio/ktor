/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.CoroutineContext

@KtorDsl
public open class WebRTCConfig {
    public var mediaTrackFactory: MediaTrackFactory? = null
    public var dispatcher: CoroutineDispatcher? = null
    public var iceServers: List<WebRTC.IceServer> = emptyList()
    public var turnServers: List<WebRTC.IceServer> = emptyList()
    public var statsRefreshRate: Long = -1
}

public interface MediaTrackFactory {
    public suspend fun createAudioTrack(constraints: WebRTCMedia.AudioTrackConstraints): WebRTCMedia.AudioTrack
    public suspend fun createVideoTrack(constraints: WebRTCMedia.VideoTrackConstraints): WebRTCMedia.VideoTrack
}

public interface WebRTCEngine : CoroutineScope, Closeable, MediaTrackFactory {
    public val config: WebRTCConfig
    public val dispatcher: CoroutineDispatcher

    public suspend fun createPeerConnection(): WebRtcPeerConnection
}

public abstract class WebRTCEngineBase(private val engineName: String) : WebRTCEngine {
    override val dispatcher: CoroutineDispatcher by lazy { config.dispatcher ?: ioDispatcher() }

    override val coroutineContext: CoroutineContext by lazy {
        dispatcher + CoroutineName("$engineName-context")
    }

    override fun close() {}
}

internal expect fun ioDispatcher(): CoroutineDispatcher

public sealed class Operation<T>(public val item: T)

public class Add<T>(item: T) : Operation<T>(item)
public class Remove<T>(item: T) : Operation<T>(item)

public abstract class WebRtcPeerConnection : Closeable {
    protected val currentStats: MutableStateFlow<List<WebRTC.Stats>> = MutableStateFlow(listOf())
    public val statsFlow: StateFlow<List<WebRTC.Stats>> = currentStats.asStateFlow()

    protected val iceCandidates: MutableSharedFlow<WebRTC.IceCandidate> = MutableSharedFlow()
    public val iceCandidateFlow: SharedFlow<WebRTC.IceCandidate> = iceCandidates.asSharedFlow()

    protected val currentIceConnectionState: MutableStateFlow<WebRTC.IceConnectionState> =
        MutableStateFlow(WebRTC.IceConnectionState.NEW)
    public val iceConnectionStateFlow: StateFlow<WebRTC.IceConnectionState> = currentIceConnectionState.asStateFlow()

    protected val remoteTracks: MutableSharedFlow<Operation<WebRTCMedia.Track>> = MutableSharedFlow()
    public val remoteTracksFlow: SharedFlow<Operation<WebRTCMedia.Track>> = remoteTracks.asSharedFlow()

    /**
     * Could be useful for some scenarios that are not covered yet
     */
    public abstract fun getNativeConnection(): Any

    public abstract suspend fun createOffer(): WebRTC.SessionDescription
    public abstract suspend fun createAnswer(): WebRTC.SessionDescription

    public abstract suspend fun setLocalDescription(description: WebRTC.SessionDescription)
    public abstract suspend fun setRemoteDescription(description: WebRTC.SessionDescription)

    public abstract suspend fun addIceCandidate(candidate: WebRTC.IceCandidate)

    public abstract suspend fun addTrack(track: WebRTCMedia.Track): WebRTC.RtpSender

    public abstract suspend fun removeTrack(sender: WebRTC.RtpSender)
    public abstract suspend fun removeTrack(track: WebRTCMedia.Track)
}
