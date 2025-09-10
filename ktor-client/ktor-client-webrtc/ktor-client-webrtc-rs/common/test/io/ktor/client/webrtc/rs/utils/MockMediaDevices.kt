/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.rs.utils

import io.ktor.client.webrtc.*
import io.ktor.client.webrtc.rs.*
import uniffi.ktor_client_webrtc.createAudioOpusTrack
import uniffi.ktor_client_webrtc.createVideoH264Track

class MockMediaDevices : MediaTrackFactory {
    var id = 0

    override suspend fun createAudioTrack(constraints: WebRtcMedia.AudioTrackConstraints): WebRtcMedia.AudioTrack {
        val nativeTrack = createAudioOpusTrack(
            trackId = "ktor-audio-track-${id++}",
            streamId = "ktor-audio-stream"
        )
        return RustAudioTrack(nativeTrack, coroutineScope = null)
    }

    override suspend fun createVideoTrack(constraints: WebRtcMedia.VideoTrackConstraints): WebRtcMedia.VideoTrack {
        val nativeTrack = createVideoH264Track(
            trackId = "ktor-video-track-${id++}",
            streamId = "ktor-video-stream"
        )
        return RustVideoTrack(nativeTrack, coroutineScope = null)
    }

    companion object {
        // Real H264 frame generated with `ffmpeg` and encoded to bytes.
        // This is needed because sending random data as H264 doesn't work.
        // ffmpeg -f lavfi -i color=c=black:s=16x16:d=0.033 -c:v libx264 -profile:v baseline -level 3.0 -tune zerolatency -x264-params "keyint=1:min-keyint=1:scenecut=0:repeat-headers=1" -f h264 - | hexdump -v -e '1/1 "0x%02X,"'
        val H264_FRAME = lazy {
            val parts = listOf(
                byteArrayOf(0, 0, 0, 1, 103, 66, -64, 30, -35, -20, 4, 64, 0, 0, 3, 0),
                byteArrayOf(64, 0, 0, 12, -93, -59, -117, -32, 0, 0, 0, 1, 104, -50, 15, 44),
                byteArrayOf(-128, 0, 0, 0, 1, 6, 5, -1, -1, 89, -36, 69, -23, -67, -26, -39),
                byteArrayOf(72, -73, -106, 44, -40, 32, -39, 35, -18, -17, 120, 50, 54, 52, 32, 45),
                byteArrayOf(32, 99, 111, 114, 101, 32, 49, 54, 52, 32, 114, 51, 49, 48, 56, 32),
                byteArrayOf(51, 49, 101, 49, 57, 102, 57, 32, 45, 32, 72, 46, 50, 54, 52, 47),
                byteArrayOf(77, 80, 69, 71, 45, 52, 32, 65, 86, 67, 32, 99, 111, 100, 101, 99),
                byteArrayOf(32, 45, 32, 67, 111, 112, 121, 108, 101, 102, 116, 32, 50, 48, 48, 51),
                byteArrayOf(45, 50, 48, 50, 51, 32, 45, 32, 104, 116, 116, 112, 58, 47, 47, 119),
                byteArrayOf(119, 119, 46, 118, 105, 100, 101, 111, 108, 97, 110, 46, 111, 114, 103, 47),
                byteArrayOf(120, 50, 54, 52, 46, 104, 116, 109, 108, 32, 45, 32, 111, 112, 116, 105),
                byteArrayOf(111, 110, 115, 58, 32, 99, 97, 98, 97, 99, 61, 48, 32, 114, 101, 102),
                byteArrayOf(61, 49, 32, 100, 101, 98, 108, 111, 99, 107, 61, 49, 58, 48, 58, 48),
                byteArrayOf(32, 97, 110, 97, 108, 121, 115, 101, 61, 48, 120, 49, 58, 48, 120, 49),
                byteArrayOf(49, 49, 32, 109, 101, 61, 104, 101, 120, 32, 115, 117, 98, 109, 101, 61),
                byteArrayOf(55, 32, 112, 115, 121, 61, 49, 32, 112, 115, 121, 95, 114, 100, 61, 49),
                byteArrayOf(46, 48, 48, 58, 48, 46, 48, 48, 32, 109, 105, 120, 101, 100, 95, 114),
                byteArrayOf(101, 102, 61, 48, 32, 109, 101, 95, 114, 97, 110, 103, 101, 61, 49, 54),
                byteArrayOf(32, 99, 104, 114, 111, 109, 97, 95, 109, 101, 61, 49, 32, 116, 114, 101),
                byteArrayOf(108, 108, 105, 115, 61, 49, 32, 56, 120, 56, 100, 99, 116, 61, 48, 32),
                byteArrayOf(99, 113, 109, 61, 48, 32, 100, 101, 97, 100, 122, 111, 110, 101, 61, 50),
                byteArrayOf(49, 44, 49, 49, 32, 102, 97, 115, 116, 95, 112, 115, 107, 105, 112, 61),
                byteArrayOf(49, 32, 99, 104, 114, 111, 109, 97, 95, 113, 112, 95, 111, 102, 102, 115),
                byteArrayOf(101, 116, 61, 45, 50, 32, 116, 104, 114, 101, 97, 100, 115, 61, 49, 32),
                byteArrayOf(108, 111, 111, 107, 97, 104, 101, 97, 100, 95, 116, 104, 114, 101, 97, 100),
                byteArrayOf(115, 61, 49, 32, 115, 108, 105, 99, 101, 100, 95, 116, 104, 114, 101, 97),
                byteArrayOf(100, 115, 61, 48, 32, 110, 114, 61, 48, 32, 100, 101, 99, 105, 109, 97),
                byteArrayOf(116, 101, 61, 49, 32, 105, 110, 116, 101, 114, 108, 97, 99, 101, 100, 61),
                byteArrayOf(48, 32, 98, 108, 117, 114, 97, 121, 95, 99, 111, 109, 112, 97, 116, 61),
                byteArrayOf(48, 32, 99, 111, 110, 115, 116, 114, 97, 105, 110, 101, 100, 95, 105, 110),
                byteArrayOf(116, 114, 97, 61, 48, 32, 98, 102, 114, 97, 109, 101, 115, 61, 48, 32),
                byteArrayOf(119, 101, 105, 103, 104, 116, 112, 61, 48, 32, 107, 101, 121, 105, 110, 116),
                byteArrayOf(61, 49, 32, 107, 101, 121, 105, 110, 116, 95, 109, 105, 110, 61, 49, 32),
                byteArrayOf(115, 99, 101, 110, 101, 99, 117, 116, 61, 48, 32, 105, 110, 116, 114, 97),
                byteArrayOf(95, 114, 101, 102, 114, 101, 115, 104, 61, 48, 32, 114, 99, 61, 99, 114),
                byteArrayOf(102, 32, 109, 98, 116, 114, 101, 101, 61, 48, 32, 99, 114, 102, 61, 50),
                byteArrayOf(51, 46, 48, 32, 113, 99, 111, 109, 112, 61, 48, 46, 54, 48, 32, 113),
                byteArrayOf(112, 109, 105, 110, 61, 48, 32, 113, 112, 109, 97, 120, 61, 54, 57, 32),
                byteArrayOf(113, 112, 115, 116, 101, 112, 61, 52, 32, 105, 112, 95, 114, 97, 116, 105),
                byteArrayOf(111, 61, 49, 46, 52, 48, 32, 97, 113, 61, 49, 58, 49, 46, 48, 48),
                byteArrayOf(0, -128, 0, 0, 0, 1, 101, -120, -124, 4, -68, -104, -96, 0, 56, -93),
                byteArrayOf(-128),
            )
            val frame = mutableListOf<Byte>()
            parts.forEach {
                frame.addAll(it.toList())
            }
            frame.toByteArray()
        }
    }
}
