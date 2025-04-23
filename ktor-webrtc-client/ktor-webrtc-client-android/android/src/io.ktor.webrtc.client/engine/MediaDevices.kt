/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.webrtc.client.engine

import android.content.Context
import android.os.Build
import io.ktor.webrtc.client.*
import io.ktor.webrtc.client.peer.*
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer.CameraEventsHandler
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.HardwareVideoEncoderFactory
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import org.webrtc.SimulcastVideoEncoderFactory
import org.webrtc.SoftwareVideoEncoderFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.*
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

public class DefaultMediaDevices(private val context: Context) : MediaTrackFactory {

    private val eglBaseContext: EglBase.Context = EglBase.create().eglBaseContext
    private val videoDecoderFactory by lazy {
        DefaultVideoDecoderFactory(eglBaseContext)
    }

    private val videoEncoderFactory by lazy {
        val hardwareEncoder = HardwareVideoEncoderFactory(eglBaseContext, true, true)
        SimulcastVideoEncoderFactory(hardwareEncoder, SoftwareVideoEncoderFactory())
    }

    private val cameraEnumerator = Camera2Enumerator(context)

    private fun findCameraId(constraints: WebRTCMedia.VideoTrackConstraints): String? {
        val targetFrontCamera = constraints.facingMode != WebRTCMedia.FacingMode.ENVIRONMENT
        return cameraEnumerator.deviceNames.firstOrNull { id ->
            if (targetFrontCamera) cameraEnumerator.isFrontFacing(id)
            else cameraEnumerator.isBackFacing(id)
        }
    }

    private val audioDeviceModule = JavaAudioDeviceModule.builder(context)
        .setUseHardwareAcousticEchoCanceler(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        .setUseHardwareNoiseSuppressor(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        .createAudioDeviceModule().also {
            it.setMicrophoneMute(false)
            it.setSpeakerMute(false)
        }

    public val peerConnectionFactory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.builder()
            .setVideoDecoderFactory(videoDecoderFactory)
            .setVideoEncoderFactory(videoEncoderFactory)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
    }

    override suspend fun createAudioTrack(constraints: WebRTCMedia.AudioTrackConstraints): WebRTCMedia.AudioTrack {
        if (constraints.latency != null) {
            throw NotImplementedError("Latency is not supported yet for Android. You can provide custom MediaTrackFactory")
        }
        if (constraints.channelCount != null) {
            throw NotImplementedError("Channel count is not supported yet for Android. You can provide custom MediaTrackFactory")
        }
        if (constraints.sampleSize != null) {
            throw NotImplementedError("Sample size is not supported yet for Android. You can provide custom MediaTrackFactory")
        }

        val items = arrayListOf<Pair<String, Boolean>>()
        items.add("googEchoCancellation" to (constraints.echoCancellation ?: true))
        items.add("googAutoGainControl" to (constraints.autoGainControl ?: true))
        items.add("googNoiseSuppression" to (constraints.noiseSuppression ?: true))

        val mediaConstraints = MediaConstraints().apply {
            mandatory.addAll(items.map { MediaConstraints.KeyValuePair(it.first, it.second.toString()) })
        }
        val id = "android-webrtc-audio-${UUID.randomUUID()}"
        val src = peerConnectionFactory.createAudioSource(mediaConstraints)
        val nativeTrack = peerConnectionFactory.createAudioTrack(id, src).apply {
            setVolume(constraints.volume ?: 1.0)
        }
        return AndroidAudioTrack(nativeTrack)
    }

    private suspend fun makeVideoSource(constraints: WebRTCMedia.VideoTrackConstraints): VideoSource {
        val cameraId = findCameraId(constraints) ?: error("No camera found for such constraints")
        val format = cameraEnumerator.getSupportedFormats(cameraId)!![0]

        val videoWidth = constraints.width ?: format.width
        val videoHeight = constraints.height ?: format.height
        val videoFrameRate = constraints.frameRate ?: min(format.framerate.max, 60)

        return suspendCoroutine { cont ->
            val eventsHandler = object : CameraEventsHandler {
                override fun onCameraError(err: String?) = cont.resumeWithException(Throwable(err ?: "Camera error"))
                override fun onCameraDisconnected() = cont.resumeWithException(Throwable("Camera disconnected"))
                override fun onCameraClosed() = cont.resumeWithException(Throwable("Camera closed"))
                override fun onCameraOpening(p0: String?) {}
                override fun onCameraFreezed(p0: String?) {}
                override fun onFirstFrameAvailable() {}
            }
            val videoCapturer = cameraEnumerator.createCapturer(cameraId, eventsHandler)
            val surfaceTextureHelper = SurfaceTextureHelper.create(
                "SurfaceTextureHelperThread",
                eglBaseContext
            )
            peerConnectionFactory.createVideoSource(false).apply {
                videoCapturer.initialize(surfaceTextureHelper, context, capturerObserver)
                videoCapturer.startCapture(videoWidth, videoHeight, videoFrameRate)
            }
        }
    }

    override suspend fun createVideoTrack(constraints: WebRTCMedia.VideoTrackConstraints): WebRTCMedia.VideoTrack {
        if (constraints.resizeMode != null) {
            throw NotImplementedError("Resize mode is not supported yet for Android. You can provide custom MediaTrackFactory")
        }
        val src = makeVideoSource(constraints)
        val id = "android-webrtc-video-${UUID.randomUUID()}"
        val nativeTrack = peerConnectionFactory.createVideoTrack(id, src)
        return AndroidVideoTrack(nativeTrack)
    }
}
