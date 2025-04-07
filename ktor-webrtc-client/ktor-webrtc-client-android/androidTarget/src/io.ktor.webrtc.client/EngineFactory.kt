/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package main.io.ktor.webrtc.client

import android.content.Context
import io.ktor.webrtc.client.WebRTCEngine
import org.webrtc.EglBase

object AndroidWebRTCEngineFactory {
    /**
     * Creates a new instance of the AndroidWebRTCEngine
     *
     * @param context Android application context
     * @param eglBase Optional EglBase instance for rendering (will create one if not provided)
     * @return WebRTCEngine implementation for Android
     */
    fun create(context: Context, eglBase: EglBase? = null): WebRTCEngine {
        return AndroidWebRTCEngine(
            context = context,
            eglBase = eglBase ?: EglBase.create()
        )
    }
}

// In your Android app
val webRTCEngine = AndroidWebRTCEngineFactory.create(applicationContext)

// In a coroutine scope
//lifecycleScope.launch {
//    // Create a peer connection with STUN and TURN servers
//    val peerConnection = webRTCEngine.createPeerConnection(
//        WebRTCConfig(
//            stunServers = listOf("stun:stun.l.google.com:19302"),
//            turnServers = listOf(
//                TurnServer(
//                    url = "turn:your-turn-server.com:3478",
//                    username = "username",
//                    credential = "password"
//                )
//            )
//        )
//    )
//
//    // Create audio and video tracks
//    val audioTrack = webRTCEngine.createAudioTrack()
//    val videoTrack = webRTCEngine.createVideoTrack()
//
//    // Add tracks to peer connection
//    peerConnection.addTrack(audioTrack)
//    peerConnection.addTrack(videoTrack)
//
//    // Collect ICE candidates
//    launch {
//        peerConnection.iceCandidateFlow.collect { iceCandidate ->
//            // Send ice candidate to remote peer through your signaling server
//        }
//    }
//
//    // Create offer and set local description
//    val offer = peerConnection.createOffer()
//    peerConnection.setLocalDescription(offer)
//
//    // Send offer to remote peer through your signaling server
//}
