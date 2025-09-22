/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.media

import WebRTC.RTCCVPixelBuffer
import WebRTC.RTCVideoCapturer
import WebRTC.RTCVideoCapturerDelegateProtocol
import WebRTC.RTCVideoFrame
import WebRTC.RTCVideoRotation
import io.ktor.client.webrtc.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreVideo.*
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalForeignApi::class)
public class MockVideoCapturer(
    private val constraints: WebRtcMedia.VideoTrackConstraints,
    private val videoCapturerDelegate: RTCVideoCapturerDelegateProtocol
) : Capturer {
    private var frameIndex = 0L
    private var timeStampNs = 0L
    private var isCapturing = false
    private var captureJob: Job? = null
    private val capturer: RTCVideoCapturer = RTCVideoCapturer(videoCapturerDelegate)

    @OptIn(DelicateCoroutinesApi::class)
    override fun startCapture() {
        require(!isCapturing) { "Capturing already started" }
        isCapturing = true
        val fps = constraints.frameRate ?: DEFAULT_FPS
        val delayDuration = (1000 / fps)

        captureJob = GlobalScope.launch {
            while (this.isActive) {
                memScoped {
                    val videoFrame = createMockVideoFrame(memScope = this, fps = fps)
                    videoCapturerDelegate.capturer(capturer, didCaptureVideoFrame = videoFrame)
                }
                frameIndex++
                timeStampNs += delayDuration * 1_000_000L
                delay(delayDuration.milliseconds)
            }
        }
    }

    override fun stopCapture() {
        (captureJob ?: error("Capturing wasn't started.")).cancel()
        isCapturing = false
        captureJob = null
    }

    private fun createMockVideoFrame(memScope: MemScope, fps: Int): RTCVideoFrame {
        val width = constraints.width ?: DEFAULT_WIDTH
        val height = constraints.height ?: DEFAULT_HEIGHT
        val pixelBuffer = createMockPixelBuffer(memScope, width, height)
        pixelBuffer.applyGradient(width, height, frameIndex, fps)
        val buffer = RTCCVPixelBuffer(pixelBuffer)
        return RTCVideoFrame(
            buffer = buffer,
            timeStampNs = timeStampNs,
            rotation = RTCVideoRotation.MIN_VALUE
        )
    }

    private fun createMockPixelBuffer(memScope: MemScope, width: Int, height: Int): CVPixelBufferRef {
        val pixelBuffer = memScope.alloc<CVPixelBufferRefVar>()
        // Create a simple RGBA pixel buffer filled with a gradient pattern
        val result = CVPixelBufferCreate(
            allocator = kCFAllocatorDefault,
            width = width.convert(),
            height = height.convert(),
            pixelFormatType = kCVPixelFormatType_32BGRA,
            pixelBufferAttributes = null,
            pixelBufferOut = pixelBuffer.ptr
        )
        require(result == kCVReturnSuccess) {
            "Failed to create CVPixelBuffer"
        }
        memScope.defer {
            CVPixelBufferRelease(texture = pixelBuffer.value)
        }
        return requireNotNull(pixelBuffer.value)
    }

    public companion object {
        public const val DEFAULT_FPS: Int = 15
        public const val DEFAULT_WIDTH: Int = 640
        public const val DEFAULT_HEIGHT: Int = 480

        public val factory: VideoCapturerFactory = { constraints, videoCapturerDelegate ->
            MockVideoCapturer(constraints, videoCapturerDelegate)
        }
    }
}

// Apply a gradient animation to the pixel buffer to create a video frame animation effect
@OptIn(ExperimentalForeignApi::class)
private fun CVPixelBufferRef.applyGradient(width: Int, height: Int, frameIndex: Long, fps: Int) {
    CVPixelBufferLockBaseAddress(pixelBuffer = this, 0u)

    val baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer = this)!!
    val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer = this).toInt()

    // Adjust animation speed based on actual FPS to maintain a consistent visual speed
    val fpsRatio = fps / 15.0
    val timePhase = frameIndex * 0.08 / fpsRatio
    val buffer = baseAddress.reinterpret<UByteVar>()

    // Multiple wave patterns for more complexity
    val waveSpeed1 = 1.5 / fpsRatio
    val waveSpeed2 = 0.8 / fpsRatio
    val waveSpeed3 = 2.2 / fpsRatio

    // Dynamic center point that moves in a figure-8 pattern
    val centerX = width / 2.0 + (width * 0.3 * sin(timePhase * 0.7))
    val centerY = height / 2.0 + (height * 0.2 * sin(timePhase * 1.4))

    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixelIndex = y * bytesPerRow + x * 4

            // Distance from a dynamic center for radial effects
            val distanceFromCenter = sqrt((x - centerX).pow(2) + (y - centerY).pow(2))
            val normalizedDistance = distanceFromCenter / (width * 0.7)

            // Multiple overlapping wave patterns
            val wave1 = sin(2 * PI * (x + frameIndex * waveSpeed1) / (width * 0.4))
            val wave2 = sin(2 * PI * (y + frameIndex * waveSpeed2) / (height * 0.3))
            val wave3 = sin(2 * PI * distanceFromCenter / 50.0 + timePhase * waveSpeed3)

            // Spiral pattern
            val angle = atan2(y - centerY, x - centerX)
            val spiral = sin(angle * 3 + distanceFromCenter * 0.02 + timePhase)

            // Combine patterns for complex interference
            val combinedWave = (wave1 + wave2 * 0.7 + wave3 * 0.5 + spiral * 0.6) / 2.8
            val intensity = (combinedWave.absoluteValue * 255).coerceIn(0.0, 255.0)

            // Dynamic color cycling with multiple frequencies
            val redPhase = timePhase + normalizedDistance * PI
            val greenPhase = timePhase * 1.3 + angle + PI / 2
            val bluePhase = timePhase * 0.7 + distanceFromCenter * 0.01 + PI

            // Create a pulsing brightness effect
            val brightness = 0.6 + 0.4 * sin(timePhase * 1.5 + normalizedDistance * 2)

            val red = (intensity * brightness * (0.5 + 0.5 * sin(redPhase))).toInt().coerceIn(0, 255).toUByte()
            val green = (intensity * brightness * (0.5 + 0.5 * sin(greenPhase))).toInt().coerceIn(0, 255).toUByte()
            val blue = (intensity * brightness * (0.5 + 0.5 * sin(bluePhase))).toInt().coerceIn(0, 255).toUByte()

            buffer[pixelIndex] = blue
            buffer[pixelIndex + 1] = green
            buffer[pixelIndex + 2] = red
            buffer[pixelIndex + 3] = 255u.toUByte()
        }
    }

    CVPixelBufferUnlockBaseAddress(pixelBuffer = this, unlockFlags = 0u)
}
