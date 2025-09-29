/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import WebRTC.RTCVideoCapturer
import WebRTC.RTCVideoCapturerDelegateProtocol
import WebRTC.RTCVideoFrame
import io.ktor.client.webrtc.media.*
import io.ktor.test.dispatcher.*
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import platform.darwin.NSObject
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalForeignApi::class)
class VideoCapturerTest {

    @OptIn(BetaInteropApi::class)
    private class TestVideoCapturerDelegate : RTCVideoCapturerDelegateProtocol, NSObject() {
        val capturedFramesCount = atomic(0)
        val capturedFrameTimestamps = Channel<Long>(capacity = Channel.UNLIMITED)

        override fun capturer(capturer: RTCVideoCapturer, didCaptureVideoFrame: RTCVideoFrame) {
            capturedFramesCount.incrementAndGet()
            val result = capturedFrameTimestamps.trySend(didCaptureVideoFrame.timeStampNs)
            assertTrue(result.isSuccess, "Failed to send frame timestamp to the channel")
        }
    }

    @Test
    fun testSampleVideoCapturerStartStop() = runTestWithRealTime {
        val constraints = WebRtcMedia.VideoTrackConstraints(
            width = 320,
            height = 240,
            frameRate = 10
        )
        val delegate = TestVideoCapturerDelegate()
        val capturer = SimulatorVideoCapturer(constraints, delegate)

        // Test initial state
        assertFalse(capturer.isCapturing)

        // Test start capture
        capturer.startCapture()
        assertTrue(capturer.isCapturing)

        withTimeout(timeout = 5.seconds) {
            val timestamp1 = delegate.capturedFrameTimestamps.receive().nanoseconds
            val timestamp2 = delegate.capturedFrameTimestamps.receive().nanoseconds
            assertTrue(timestamp2 - timestamp1 >= 100.milliseconds, "Expected timestamps to be in order")
            assertTrue(timestamp2 - timestamp1 <= 1.seconds, "Should be less than 1 second")
        }
        // Test stop capture
        capturer.stopCapture()
        assertFalse(capturer.isCapturing)
        val lastCapturedFrame = delegate.capturedFramesCount.value

        delay(200.milliseconds)
        assertEquals(lastCapturedFrame, delegate.capturedFramesCount.value, "Should not capture frames after stop")
    }

    @Test
    fun testSampleVideoCapturerFrameRate() = runTestWithRealTime {
        val targetFps = 10
        val constraints = WebRtcMedia.VideoTrackConstraints(frameRate = targetFps)
        val delegate = TestVideoCapturerDelegate()
        val capturer = SimulatorVideoCapturer(constraints, delegate)

        // calculating frames could take too long on some platforms
        capturer.precalculateFrames()
        capturer.startCapture()
        delay(1.seconds)
        capturer.stopCapture()

        // Allow for some variance in frame rate (±2 frames)
        val realFps = delegate.capturedFramesCount.value
        assertTrue(
            realFps in (targetFps - 2)..targetFps,
            "Expected around $targetFps frames, got $realFps"
        )
    }

    @Test
    fun testSampleVideoCapturerInvalidStartStop() {
        val constraints = WebRtcMedia.VideoTrackConstraints()
        val delegate = TestVideoCapturerDelegate()
        val capturer = SimulatorVideoCapturer(constraints, delegate)

        assertFailsWith<IllegalStateException> {
            capturer.stopCapture() // Should throw
        }

        capturer.startCapture()

        assertFailsWith<IllegalArgumentException> {
            capturer.startCapture() // Should throw
        }

        capturer.stopCapture()
    }
}
