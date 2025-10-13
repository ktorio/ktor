/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.media

import dev.onvoid.webrtc.media.MediaDevices
import dev.onvoid.webrtc.media.video.VideoCaptureCapability
import dev.onvoid.webrtc.media.video.VideoDeviceSource
import io.ktor.client.webrtc.WebRtcMedia
import kotlin.math.abs

internal class VideoTrackCapturer : VideoCapturer {
    override val source: VideoDeviceSource = VideoDeviceSource()

    override fun start() = source.start()

    override fun stop() = source.stop()

    override fun close() = source.dispose()
}

/**
 * This factory automatically selects the best available camera device and capability
 * based on the provided constraints. It uses a fitness algorithm to find the closest
 * match for the requested video dimensions and frame rate.
 */
public class CameraVideoFactory : VideoFactory {

    /**
     * Creates a video capturer with the best matching camera capability.
     *
     * Selects the camera and video capability with dimensions closest to the target
     * resolution and frame rate equal to or higher than requested.
     *
     * @throws WebRtcMedia.DeviceException if no suitable video device is found
     */
    override fun createVideoCapturer(constraints: WebRtcMedia.VideoTrackConstraints): VideoCapturer {
        val targetWidth = constraints.width ?: DEFAULT_VIDEO_WIDTH
        val targetHeight = constraints.height ?: DEFAULT_VIDEO_HEIGHT
        val targetFrameRate = constraints.frameRate ?: DEFAULT_VIDEO_FRAME_RATE

        val allDevices = MediaDevices.getVideoCaptureDevices()

        val capabilitiesByDimensions = allDevices
            .flatMap { MediaDevices.getVideoCaptureCapabilities(it) }
            .groupBy { it.width to it.height }

        val bestDimension = capabilitiesByDimensions.keys.minByOrNull { key ->
            val capability = capabilitiesByDimensions.getValue(key).first()
            capability.fitnessDistance(targetWidth, targetHeight)
        } ?: throw WebRtcMedia.DeviceException("No suitable video device found.")

        val bestCapability = capabilitiesByDimensions
            .getValue(bestDimension)
            .sortedBy { it.frameRate }
            .first { it.frameRate >= targetFrameRate }

        val selectedDevice = allDevices.first { device ->
            MediaDevices.getVideoCaptureCapabilities(device).any { it == bestCapability }
        }

        return VideoTrackCapturer().apply {
            source.setVideoCaptureDevice(selectedDevice)
            source.setVideoCaptureCapability(bestCapability)
        }
    }

    public companion object {
        public const val DEFAULT_VIDEO_WIDTH: Int = 1280
        public const val DEFAULT_VIDEO_HEIGHT: Int = 720
        public const val DEFAULT_VIDEO_FRAME_RATE: Int = 30
    }
}

private fun VideoCaptureCapability.fitnessDistance(targetWidth: Int, targetHeight: Int): Int {
    return abs(width - targetWidth) + abs(height - targetHeight)
}
