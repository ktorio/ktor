///*
// * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
// */
//
//package io.ktor.webrtc.client
//
//import io.ktor.webrtc.client.engine.*
//import io.ktor.webrtc.test.*
//import kotlinx.coroutines.test.runTest
//import kotlin.test.Test
//import kotlin.test.assertNotNull
//import kotlin.test.assertTrue
//
//class JsWebRTCEngineTest : WebRTCEngineIntegrationTest() {
//    override fun createClient(): WebRTCClient {
//        return WebRTCClient(JsWebRTC) {
//            iceServers = this.iceServers
//            turnServers = this.turnServers
//            statsRefreshRate = 1000 // 1 second refresh rate for stats in JS engine
//        }
//    }
//
//    @Test
//    fun testJsSpecificBrowserMediaAccess() = runTest {
//        // Test JS-specific functionality such as browser media access
//        val engine = createClient()
//
//        try {
//            val videoTrack = engine.createVideoTrack()
//            assertNotNull(videoTrack, "Video track should be successfully created in JS environment")
//
//            // JS-specific assertions related to the DOM and media elements
//            // (Implementation would depend on how the JsWebRTCEngine exposes this functionality)
//
//            videoTrack.stop()
//        } catch (e: Exception) {
//            // In headless test environments without camera access, this may fail
//            // but should fail with a specific error related to media device access
//            assertTrue(
//                e.message?.contains("media") == true ||
//                    e.message?.contains("permission") == true ||
//                    e.message?.contains("device") == true,
//                "Expected media device related error, got: ${e.message}"
//            )
//        }
//
//        engine.close()
//    }
//
//    @Test
//    fun testBrowserCompatibility() = runTest {
//        // Test browser compatibility features
//        val engine = createClient()
//        val peerConnection = engine.createPeerConnection()
//
//        // Create an offer and check for browser-specific SDP patterns
//        val offer = peerConnection.createOffer()
//
//        // Different browsers may have different SDP formats, but all should contain these
//        assertTrue(offer.sdp.contains("a=fingerprint"), "SDP should contain fingerprint attribute")
//        assertTrue(offer.sdp.contains("a=ice-options"), "SDP should contain ICE options")
//
//        peerConnection.close()
//        engine.close()
//    }
//}
