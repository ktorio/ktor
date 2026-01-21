/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.ktor.client.webrtc.*
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.*
import org.webrtc.CameraVideoCapturer.CameraEventsHandler
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MediaDevicesFactory based on the org.webrtc, which uses Android Camera2 API
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.webrtc.media.AndroidMediaDevices)
 **/
public class AndroidMediaDevices(
    private val context: Context,
    eglBase: EglBase = EglBase.create()
) : MediaTrackFactory {

    private val eglBaseContext: EglBase.Context = eglBase.eglBaseContext

    private val videoDecoderFactory by lazy {
        DefaultVideoDecoderFactory(eglBaseContext)
    }
    private val videoEncoderFactory by lazy {
        DefaultVideoEncoderFactory(eglBaseContext, true, true)
    }

    private val cameraEnumerator = Camera2Enumerator(context)

    private fun findCameraId(constraints: WebRtcMedia.VideoTrackConstraints): String? {
        val targetFrontCamera = constraints.facingMode != WebRtcMedia.FacingMode.ENVIRONMENT
        return cameraEnumerator.deviceNames.firstOrNull { id ->
            if (targetFrontCamera) {
                cameraEnumerator.isFrontFacing(id)
            } else {
                cameraEnumerator.isBackFacing(id)
            }
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
        if (!libraryInitialized) {
            val options = PeerConnectionFactory.InitializationOptions
                .builder(context)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)
            libraryInitialized = true
        }

        PeerConnectionFactory
            .builder()
            .setVideoDecoderFactory(videoDecoderFactory)
            .setVideoEncoderFactory(videoEncoderFactory)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }

    private fun assertPermission(permission: String) {
        val status = context.checkCallingOrSelfPermission(permission)
        if (status != PackageManager.PERMISSION_GRANTED) {
            throw WebRtcMedia.PermissionException(permission)
        }
    }

    override suspend fun createAudioTrack(constraints: WebRtcMedia.AudioTrackConstraints): WebRtcMedia.AudioTrack {
        if (constraints.latency != null) {
            TODO("Latency is not supported yet for Android. You can provide custom MediaTrackFactory")
        }
        if (constraints.channelCount != null) {
            TODO("Channel count is not supported yet for Android. You can provide custom MediaTrackFactory")
        }
        if (constraints.sampleSize != null) {
            TODO("Sample size is not supported yet for Android. You can provide custom MediaTrackFactory")
        }
        assertPermission(Manifest.permission.RECORD_AUDIO)

        val mc = arrayListOf<Pair<String, Boolean>>()
        mc.add("googEchoCancellation" to (constraints.echoCancellation != false))
        mc.add("googAutoGainControl" to (constraints.autoGainControl != false))
        mc.add("googNoiseSuppression" to (constraints.noiseSuppression != false))

        val mediaConstraints = MediaConstraints().apply {
            mandatory.addAll(
                mc.map {
                    MediaConstraints.KeyValuePair(
                        it.first,
                        it.second.toString()
                    )
                }
            )
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }
        val id = "android-webrtc-audio-${UUID.randomUUID()}"
        val src = peerConnectionFactory.createAudioSource(mediaConstraints)
        val nativeTrack = peerConnectionFactory.createAudioTrack(id, src).apply {
            setVolume(constraints.volume ?: 1.0)
        }
        return AndroidAudioTrack(nativeTrack) {
            src.dispose()
        }
    }

    private suspend fun makeVideoSource(constraints: WebRtcMedia.VideoTrackConstraints): Pair<VideoSource, () -> Unit> {
        val cameraId = findCameraId(constraints) ?: error("No camera found for such constraints")
        val format = cameraEnumerator.getSupportedFormats(cameraId)?.firstOrNull()
            ?: error("No supported formats for camera $cameraId")

        val videoWidth = constraints.width ?: format.width
        val videoHeight = constraints.height ?: format.height
        val videoFrameRate = constraints.frameRate ?: DEFAULT_FRAME_RATE

        return suspendCancellableCoroutine { cont ->
            var videoSource: VideoSource? = null
            var onDispose = {}
            val eventsHandler = object : CameraEventsHandler {
                override fun onCameraError(err: String?) {
                    onDispose()
                    if (cont.isActive) cont.resumeWithException(WebRtcMedia.DeviceException(err ?: "Camera error"))
                }

                override fun onCameraDisconnected() {
                    onDispose()
                    if (cont.isActive) cont.resumeWithException(WebRtcMedia.DeviceException("Camera disconnected"))
                }

                override fun onCameraClosed() {
                    if (cont.isActive) cont.resumeWithException(WebRtcMedia.DeviceException("Camera closed"))
                }

                override fun onFirstFrameAvailable() {
                    if (cont.isActive) cont.resume(requireNotNull(videoSource) to onDispose)
                }

                override fun onCameraOpening(cameraId: String?) = Unit
                override fun onCameraFreezed(p0: String?) = Unit
            }
            val videoCapturer = cameraEnumerator.createCapturer(cameraId, eventsHandler)
            val surfaceTextureHelper = SurfaceTextureHelper.create(
                "SurfaceTextureHelperThread",
                eglBaseContext
            )
            onDispose = {
                videoCapturer.stopCapture()
                videoCapturer.dispose()
                surfaceTextureHelper.dispose()
                videoSource?.dispose()
            }
            // video source will be ready or throw an error (if the configuration is wrong) later
            videoSource = peerConnectionFactory.createVideoSource(false).apply {
                videoCapturer.initialize(surfaceTextureHelper, context, capturerObserver)
                videoCapturer.startCapture(videoWidth, videoHeight, videoFrameRate)
            }
        }
    }

    override suspend fun createVideoTrack(constraints: WebRtcMedia.VideoTrackConstraints): WebRtcMedia.VideoTrack {
        if (constraints.resizeMode != null) {
            TODO("Resize mode is not supported yet for Android. You can provide custom MediaTrackFactory")
        }
        assertPermission(Manifest.permission.CAMERA)

        val (src, onDispose) = makeVideoSource(constraints)
        val id = "android-webrtc-video-${UUID.randomUUID()}"
        val nativeTrack = peerConnectionFactory.createVideoTrack(id, src)
        return AndroidVideoTrack(nativeTrack) { onDispose() }
    }

    private companion object {
        var libraryInitialized = false
        const val DEFAULT_FRAME_RATE = 30
    }
}
