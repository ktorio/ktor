/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client

import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext

@KtorDsl
public open class WebRTCConfig {
    public var dispatcher: CoroutineDispatcher? = null
    public var iceServers: List<IceServer> = emptyList()
    public var turnServers: List<IceServer> = emptyList()
    public var statsRefreshRate: Long = -1
}

public data class IceServer(
    val urls: String,
    val username: String? = null,
    val credential: String? = null
)

public interface WebRTCEngine : CoroutineScope, Closeable {
    public val dispatcher: CoroutineDispatcher
    public val config: WebRTCConfig

    public suspend fun createPeerConnection(): WebRtcPeerConnection

    public suspend fun createAudioTrack(): WebRTCMediaTrack
    public suspend fun createVideoTrack(): WebRTCMediaTrack
}

public abstract class WebRTCEngineBase(private val engineName: String) : WebRTCEngine {
    private val closed = atomic(false)

    override val dispatcher: CoroutineDispatcher by lazy { config.dispatcher ?: ioDispatcher() }

    override val coroutineContext: CoroutineContext by lazy {
        SilentSupervisor() + dispatcher + CoroutineName("$engineName-context")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val requestJob = coroutineContext[Job] as? CompletableJob ?: return
        requestJob.complete()
    }
}

internal expect fun ioDispatcher(): CoroutineDispatcher

public interface WebRtcPeerConnection : Closeable {
    public val iceCandidateFlow: SharedFlow<IceCandidate>
    public val statsFlow: StateFlow<WebRTCStatsReport>

    public suspend fun createOffer(): SessionDescription
    public suspend fun createAnswer(): SessionDescription

    public suspend fun setLocalDescription(description: SessionDescription)
    public suspend fun setRemoteDescription(description: SessionDescription)

    public suspend fun addIceCandidate(candidate: IceCandidate)

    public suspend fun addTrack(track: WebRTCMediaTrack)
    public suspend fun removeTrack(track: WebRTCMediaTrack)

    public data class IceCandidate(
        public val candidate: String,
        public val sdpMid: String?,
        public val sdpMLineIndex: Int?
    )

    public data class SessionDescription(
        val type: SessionDescriptionType,
        val sdp: String
    )

    public enum class SessionDescriptionType {
        OFFER,
        ANSWER,
        PROVISIONAL_ANSWER,
        ROLLBACK
    }
}

public sealed class WebRTCMediaStats(
    public val timestamp: Long,
    public val trackId: String,
    public val type: String,
    public val id: String,
)

public class WebRTCVideoSourceStats(
    timestamp: Long,
    trackId: String,
    type: String,
    id: String,
    public val width: Int?,
    public val height: Int?,
    public val frames: Int?,
    public val framesPerSecond: Int?,
) : WebRTCMediaStats(timestamp, trackId, type, id)

public class WebRTCAudioSourceStats(
    timestamp: Long,
    trackId: String,
    type: String,
    id: String,
    public val audioLevel: Double?,
    public val totalAudioEnergy: Double?,
    public val totalSamplesDuration: Double?,
) : WebRTCMediaStats(timestamp, trackId, type, id)

public data class WebRTCStatsReport(
    val timestamp: Long,
    val audio: WebRTCAudioSourceStats?,
    val video: WebRTCVideoSourceStats?
)

public val EmptyWebRTCStatsReport: WebRTCStatsReport = WebRTCStatsReport(0L, null, null)

public interface WebRTCMediaSource

public interface WebRTCAudioSource : WebRTCMediaSource

public interface WebRTCVideoSource : WebRTCMediaSource {
    public val isScreencast: Boolean
}

public interface WebRTCMediaTrack {
    public val id: String
    public val kind: Type
    public val enabled: Boolean

    public fun enable(enabled: Boolean)
    public fun stop()

    public enum class Type {
        AUDIO,
        VIDEO,
    }
}
