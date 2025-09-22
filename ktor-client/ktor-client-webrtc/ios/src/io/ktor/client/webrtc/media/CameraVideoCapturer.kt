/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.media

import WebRTC.RTCCameraVideoCapturer
import WebRTC.RTCVideoCapturerDelegateProtocol
import io.ktor.client.webrtc.WebRtcMedia
import io.ktor.client.webrtc.toIos
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceFormat
import platform.AVFoundation.AVCaptureDevicePosition
import platform.AVFoundation.AVCaptureMultiCamSession
import platform.AVFoundation.AVFrameRateRange
import platform.AVFoundation.multiCamSupported
import platform.AVFoundation.position
import platform.CoreMedia.CMFormatDescriptionGetMediaSubType
import platform.CoreMedia.CMVideoFormatDescriptionGetDimensions
import platform.darwin.FourCharCode
import kotlin.math.abs

@OptIn(ExperimentalForeignApi::class)
public class CameraVideoCapturer(
    private val constraints: WebRtcMedia.VideoTrackConstraints,
    videoCapturerDelegate: RTCVideoCapturerDelegateProtocol
) : Capturer {
    private val videoCapturer = RTCCameraVideoCapturer(videoCapturerDelegate)

    private val device = lazy {
        val position = constraints.facingMode?.toIos()
        selectDevice(position) ?: error("No camera found for the defined facing mode.")
    }

    private val format: AVCaptureDeviceFormat by lazy {
        selectFormat(
            device.value,
            targetWidth = constraints.width,
            targetHeight = constraints.height,
            preferredOutputPixelFormat = videoCapturer.preferredOutputPixelFormat()
        ) ?: error("No camera device found.")
    }

    override fun startCapture() {
        require(!device.isInitialized()) { "Capturing was already started." }
        val targetFps = constraints.frameRate?.toDouble() ?: DEFAULT_FPS
        val fps = selectFps(format = format, targetFps = targetFps)
        videoCapturer.startCaptureWithDevice(device.value, format, fps.toLong())
    }

    override fun stopCapture() {
        require(device.isInitialized()) { "Capturing was not started." }
        videoCapturer.stopCapture()
    }

    public companion object {
        public const val DEFAULT_FPS: Double = 30.0

        public val factory: VideoCapturerFactory = { constraints, videoCapturerDelegate ->
            CameraVideoCapturer(constraints, videoCapturerDelegate)
        }

        private fun selectDevice(position: AVCaptureDevicePosition?): AVCaptureDevice? {
            return RTCCameraVideoCapturer.captureDevices().firstOrNull {
                position == null || (it as AVCaptureDevice).position == position
            } as AVCaptureDevice?
        }

        private fun selectFormat(
            device: AVCaptureDevice,
            targetWidth: Int?,
            targetHeight: Int?,
            preferredOutputPixelFormat: FourCharCode
        ): AVCaptureDeviceFormat? {
            val formats = RTCCameraVideoCapturer.supportedFormatsForDevice(device)
            var selectedFormat: AVCaptureDeviceFormat? = null
            var currentDiff = Int.MAX_VALUE

            for (format in formats) {
                val format = format as AVCaptureDeviceFormat
                if (format.multiCamSupported != AVCaptureMultiCamSession.multiCamSupported) {
                    continue
                }
                CMVideoFormatDescriptionGetDimensions(format.formatDescription).useContents {
                    val pixelFormat = CMFormatDescriptionGetMediaSubType(format.formatDescription)
                    val deltaWidth = targetWidth?.let { abs(it - width) } ?: 0
                    val deltaHeight = targetHeight?.let { abs(it - height) } ?: 0
                    val diff = deltaWidth + deltaHeight

                    if (diff < currentDiff) {
                        selectedFormat = format
                        currentDiff = diff
                    } else if (diff == currentDiff && pixelFormat == preferredOutputPixelFormat) {
                        selectedFormat = format
                    }
                }
            }

            return selectedFormat
        }

        private fun selectFps(format: AVCaptureDeviceFormat, targetFps: Double): Double {
            val maxSupportedFrameRate = format.videoSupportedFrameRateRanges.maxOf {
                (it as AVFrameRateRange).maxFrameRate
            }
            return targetFps.coerceAtMost(maxSupportedFrameRate)
        }
    }
}
