/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalForeignApi::class)

package io.ktor.client.webrtc.media

import WebRTC.RTCCVPixelBuffer
import WebRTC.RTCVideoCapturer
import WebRTC.RTCVideoCapturerDelegateProtocol
import WebRTC.RTCVideoFrame
import io.ktor.client.webrtc.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreVideo.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * Sample video capturer that generates synthetic video frames with animated gradient patterns.
 *
 * This capturer is particularly useful in iOS simulators where camera access is not available,
 * as it emits video with moving gradients and color patterns to verify actual video transfer
 * functionality during development and testing.
 *
 * **Performance Optimization: **
 * This implementation uses frame caching to achieve optimal performance. A set of pre-calculated
 * frames (15) are generated once during initialization and then cycled through during
 * video capture. This eliminates the need for real-time pixel calculations, significantly
 * improving frame generation speed.
 *
 * @param constraints Video track constraints specifying frame dimensions and rate
 * @param videoCapturerDelegate Delegate for handling captured video frames
 */
public class SimulatorVideoCapturer(
    private val constraints: WebRtcMedia.VideoTrackConstraints,
    private val videoCapturerDelegate: RTCVideoCapturerDelegateProtocol
) : Capturer {
    private var frameIndex = 0
    private var timeStampNs = 0L
    override var isCapturing: Boolean = false
        private set

    private var job: Job? = null
    private val scope = CoroutineScope(context = SupervisorJob() + Dispatchers.IO)
    private val capturer: RTCVideoCapturer = RTCVideoCapturer(videoCapturerDelegate)

    private val fps = constraints.frameRate ?: DEFAULT_FPS
    private val delayDuration = (1000 / fps).milliseconds

    private val arena = Arena()
    private val lazyBuffers = (0..<min(DEFAULT_FPS, fps)).map { frameIndex ->
        lazy { createVideoBuffer(frameIndex) }
    }

    @OptIn(ExperimentalNativeApi::class)
    internal fun precalculateFrames() {
        for (buffer in lazyBuffers) {
            assert(buffer.value.width > 0)
        }
    }

    private fun nextFrame(): RTCVideoFrame {
        val buffer = lazyBuffers[frameIndex].value
        timeStampNs += delayDuration.inWholeNanoseconds
        frameIndex = (frameIndex + 1) % lazyBuffers.size
        return RTCVideoFrame(
            rotation = 0,
            buffer = buffer,
            timeStampNs = timeStampNs
        )
    }

    override fun startCapture() {
        require(!isCapturing) { "Capturing already started" }
        isCapturing = true

        job = scope.launch {
            while (isActive) {
                val calculationTime = measureTime {
                    videoCapturerDelegate.capturer(capturer, nextFrame())
                }
                val duration = delayDuration.minus(calculationTime)
                if (duration.isPositive() && isActive) {
                    delay(duration)
                }
            }
        }
    }

    override fun stopCapture() {
        if (!isCapturing) error("Capturing wasn't started.")
        scope.coroutineContext.cancelChildren()
        isCapturing = false
    }

    private fun createVideoBuffer(frameIndex: Int): RTCCVPixelBuffer {
        val width = constraints.width ?: DEFAULT_WIDTH
        val height = constraints.height ?: DEFAULT_HEIGHT
        val pixelBuffer = createPixelBuffer(width, height).apply {
            applyGradient(width, height, frameIndex)
        }
        return RTCCVPixelBuffer(pixelBuffer)
    }

    private fun createPixelBuffer(width: Int, height: Int): CVPixelBufferRef {
        val pixelBuffer = arena.alloc<CVPixelBufferRefVar>()
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
        arena.defer {
            CVPixelBufferRelease(texture = pixelBuffer.value)
        }
        return requireNotNull(pixelBuffer.value)
    }

    override fun close() {
        if (isCapturing) stopCapture()
        job?.invokeOnCompletion { arena.clear() }
    }

    public companion object Companion : VideoCapturerFactory {
        /**
         * Default frames per second rate used by the sample video capturer (15 FPS).
         */
        public const val DEFAULT_FPS: Int = 15

        /**
         * Default video frame width in pixels (640px).
         */
        public const val DEFAULT_WIDTH: Int = 640

        /**
         * Default video frame height in pixels (480px).
         */
        public const val DEFAULT_HEIGHT: Int = 480

        override fun create(
            constraints: WebRtcMedia.VideoTrackConstraints,
            delegate: RTCVideoCapturerDelegateProtocol
        ): Capturer {
            return SimulatorVideoCapturer(constraints, videoCapturerDelegate = delegate)
        }
    }
}

// Apply a gradient animation to the pixel buffer to create a video frame animation effect
@OptIn(ExperimentalForeignApi::class)
private fun CVPixelBufferRef.applyGradient(width: Int, height: Int, frameIndex: Int) {
    CVPixelBufferLockBaseAddress(pixelBuffer = this, 0u)

    val baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer = this)!!
    val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer = this).toInt()

    val time = frameIndex * 0.1
    val buffer = baseAddress.reinterpret<UByteVar>()

    val waveOffsetX = (time * 50).toInt()
    val waveOffsetY = (time * 30).toInt()

    for (y in 0 until height) {
        for (x in 0 until width) {
            val pixelIndex = y * bytesPerRow + x * 4

            val wave = ((x + waveOffsetX) + (y + waveOffsetY)) % 512
            val intensity = if (wave < 256) wave else 511 - wave

            val red = ((intensity + time * 20) % 256).toInt().coerceIn(0, 255).toUByte()
            val green = ((intensity + time * 40) % 256).toInt().coerceIn(0, 255).toUByte()
            val blue = ((intensity + time * 60) % 256).toInt().coerceIn(0, 255).toUByte()

            buffer[pixelIndex] = blue
            buffer[pixelIndex + 1] = green
            buffer[pixelIndex + 2] = red
            buffer[pixelIndex + 3] = 255u
        }
    }

    CVPixelBufferUnlockBaseAddress(pixelBuffer = this, unlockFlags = 0u)
}
