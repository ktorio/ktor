/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import dev.onvoid.webrtc.media.video.CustomVideoSource
import dev.onvoid.webrtc.media.video.NativeI420Buffer
import dev.onvoid.webrtc.media.video.VideoFrame
import io.ktor.client.webrtc.media.*
import io.ktor.client.webrtc.utils.*
import kotlin.time.Duration.Companion.milliseconds

class MockVideoCapturer(
    private val width: Int,
    private val height: Int,
    frameRate: Int
) : VideoCapturer {

    override val source: CustomVideoSource = CustomVideoSource()
    private val ticker = Ticker((1000 / frameRate).milliseconds) {
        pushNextVideoFrame()
    }

    private fun pushNextVideoFrame() {
        val buffer = NativeI420Buffer.allocate(width, height)
        // skip filling the buffer
        val frame = VideoFrame(buffer, System.nanoTime())
        source.pushFrame(frame)
        frame.release()
    }

    override fun start() {
        ticker.start()
    }

    override fun stop() {
        ticker.stop()
    }

    override fun close() {
        stop()
        source.dispose()
    }
}

class MockVideoFactory : VideoFactory {

    override fun createVideoCapturer(constraints: WebRtcMedia.VideoTrackConstraints): VideoCapturer {
        return MockVideoCapturer(
            constraints.width ?: DEFAULT_WIDTH,
            constraints.height ?: DEFAULT_HEIGHT,
            constraints.frameRate ?: DEFAULT_FPS
        )
    }

    companion object {
        const val DEFAULT_FPS: Int = 15
        const val DEFAULT_WIDTH: Int = 640
        const val DEFAULT_HEIGHT: Int = 480
    }
}
