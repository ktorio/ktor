/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext

@KtorDsl
public open class WebRTCConfig {
    public var mediaTrackFactory: MediaTrackFactory? = null
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

public enum class FacingMode {
    USER,
    ENVIRONMENT,
    LEFT,
    RIGHT;
}

public enum class ResizeMode {
    NONE,
    CROP_AND_SCALE
}

public data class VideoTrackConstraints(
    val width: Int? = null,
    val height: Int? = null,
    val aspectRatio: Double? = null,
    val frameRate: Double? = null,
    val facingMode: FacingMode? = null,
    val resizeMode: ResizeMode? = null,
)

public data class AudioTrackConstraints(
    var volume: Double? = null,
    var sampleRate: Int? = null,
    var sampleSize: Int? = null,
    var echoCancellation: Boolean? = null,
    var autoGainControl: Boolean? = null,
    var noiseSuppression: Boolean? = null,
    var latency: Double? = null,
    var channelCount: Int? = null,
)

public interface MediaTrackFactory {
    public suspend fun createAudioTrack(constraints: AudioTrackConstraints): WebRTCAudioTrack
    public suspend fun createVideoTrack(constraints: VideoTrackConstraints): WebRTCVideoTrack
}

public interface WebRTCEngine : CoroutineScope, Closeable, MediaTrackFactory {
    public val config: WebRTCConfig
    public val dispatcher: CoroutineDispatcher

    public suspend fun createPeerConnection(): WebRtcPeerConnection
}

public abstract class WebRTCEngineBase(private val engineName: String) : WebRTCEngine {
    private val closed = atomic(false)

    override val dispatcher: CoroutineDispatcher by lazy { config.dispatcher ?: ioDispatcher() }

    override val coroutineContext: CoroutineContext by lazy {
        dispatcher + CoroutineName("$engineName-context")
        //SilentSupervisor() + dispatcher + CoroutineName("$engineName-context")
    }

    override fun close() {
//        if (!closed.compareAndSet(false, true)) return
//        val requestJob = coroutineContext[Job] as? CompletableJob ?: return
//        requestJob.complete()
    }
}

internal expect fun ioDispatcher(): CoroutineDispatcher

public interface WebRtcPeerConnection : Closeable {
    public val iceCandidateFlow: SharedFlow<IceCandidate>
    public val statsFlow: StateFlow<List<WebRTCStats>>

    // That could be useful for some scenarios that are not covered yet
    public fun getNativeConnection(): Any?

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
    public val extra: Map<String, Any> = emptyMap()
)

public data class WebRTCStats(
    val id: String,
    val type: String,
    val timestamp: Long,
    val props: Map<String, Any?>,
)

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

public interface WebRTCVideoTrack : WebRTCMediaTrack
public interface WebRTCAudioTrack : WebRTCMediaTrack
