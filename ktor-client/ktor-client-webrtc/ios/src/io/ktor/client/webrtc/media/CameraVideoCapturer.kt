/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.media

import WebRTC.RTCCameraVideoCapturer
import WebRTC.RTCVideoCapturerDelegateProtocol
import io.ktor.client.webrtc.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.*
import platform.CoreMedia.CMFormatDescriptionGetMediaSubType
import platform.CoreMedia.CMVideoFormatDescriptionGetDimensions
import platform.darwin.FourCharCode
import kotlin.math.abs

/**
 * Camera video capturer that captures live video from device cameras.
 *
 * This capturer automatically selects the camera device and format that most closely matches
 * the provided video track constraints. It chooses the camera format with dimensions nearest
 * to the requested width and height, adjusts frame rate to the maximum supported rate
 * that doesn't exceed the requested target.
 *
 * @param constraints Video track constraints specifying desired camera parameters
 * @param videoCapturerDelegate Delegate for handling captured video frames
 */
@OptIn(ExperimentalForeignApi::class)
public class CameraVideoCapturer(
    private val constraints: WebRtcMedia.VideoTrackConstraints,
    videoCapturerDelegate: RTCVideoCapturerDelegateProtocol
) : Capturer {
    override var isCapturing: Boolean = false
        private set
    private val videoCapturer = RTCCameraVideoCapturer(videoCapturerDelegate)

    private val device by lazy {
        val position = constraints.facingMode?.toIos()
        selectDevice(position) ?: error("No camera found for the defined facing mode.")
    }

    private val format: AVCaptureDeviceFormat by lazy {
        selectFormat(
            device,
            targetWidth = constraints.width,
            targetHeight = constraints.height,
            preferredOutputPixelFormat = videoCapturer.preferredOutputPixelFormat()
        ) ?: error("No camera device found.")
    }

    override fun startCapture() {
        require(!isCapturing) { "Capturing was already started." }
        isCapturing = true
        val targetFps = constraints.frameRate?.toDouble() ?: DEFAULT_FPS
        val fps = selectFps(format = format, targetFps = targetFps)
        videoCapturer.startCaptureWithDevice(device, format, fps.toLong())
    }

    override fun stopCapture() {
        require(isCapturing) { "Capturing was not started." }
        isCapturing = false
        videoCapturer.stopCapture()
    }

    public companion object : VideoCapturerFactory {
        /**
         * Default frames per-second rate used by the camera video capturer.
         */
        public const val DEFAULT_FPS: Double = 30.0

        public override fun create(
            constraints: WebRtcMedia.VideoTrackConstraints,
            delegate: RTCVideoCapturerDelegateProtocol
        ): Capturer {
            return CameraVideoCapturer(constraints, videoCapturerDelegate = delegate)
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
