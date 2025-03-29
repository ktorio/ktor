///*
// * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
// */
//
//package io.ktor.webrtc.client
//
///*
// * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
// */
//
//package io.ktor.webrtc.client.plugins
//
//import io.ktor.util.*
//import io.ktor.webrtc.client.*
//import kotlinx.coroutines.*
//import kotlinx.coroutines.flow.*
//
///**
// * A plugin that provides functionality to auto-reconnect WebRTC connections when they're dropped.
// */
//public class WebRTCAutoReconnect(
//    private val config: Config
//) {
//    /**
//     * Configuration for [WebRTCAutoReconnect].
//     */
//    @KtorDsl
//    public class Config {
//        /**
//         * Maximum number of reconnection attempts.
//         * Default is 5.
//         */
//        public var maxAttempts: Int = 5
//
//        /**
//         * Initial delay before first reconnection attempt in milliseconds.
//         * Default is 1000ms (1 second).
//         */
//        public var initialDelay: Long = 1000
//
//        /**
//         * Maximum delay between reconnection attempts in milliseconds.
//         * Default is 30000ms (30 seconds).
//         */
//        public var maxDelay: Long = 30000
//
//        /**
//         * Multiplier for exponential backoff.
//         * Default is 1.5.
//         */
//        public var multiplier: Double = 1.5
//    }
//
//    /**
//     * Companion object for [WebRTCAutoReconnect].
//     */
//    public companion object Feature : WebRTCClientFeature<Config, WebRTCAutoReconnect> {
//        override val key: AttributeKey<WebRTCAutoReconnect> = AttributeKey("WebRTCAutoReconnect")
//
//        override fun prepare(block: Config.() -> Unit): (WebRTCClient) -> Unit {
//            val config = Config().apply(block)
//            return { client ->
//                install(client, WebRTCAutoReconnect(config))
//            }
//        }
//
//        override fun install(feature: WebRTCAutoReconnect, scope: WebRTCClient) {
//            // Setup reconnection logic
//            scope.events.subscribe("WebRTCConnectionClosed") { peerConnection ->
//                if (peerConnection is WebRtcPeerConnection) {
//                    CoroutineScope(scope.coroutineContext).launch {
//                        handleReconnect(peerConnection, feature.config, scope)
//                    }
//                }
//            }
//        }
//
//        private suspend fun handleReconnect(
//            peerConnection: WebRtcPeerConnection,
//            config: Config,
//            client: WebRTCClient
//        ) {
//            var attempts = 0
//            var currentDelay = config.initialDelay
//
//            while (attempts < config.maxAttempts) {
//                delay(currentDelay)
//                attempts++
//
//                try {
//                    // Attempt to reconnect
//                    val newConnection = client.createPeerConnection {
//                        // Copy existing configuration
//                    }
//
//                    // Emit reconnection event with the new connection
//                    client.events.raise("WebRTCReconnected", newConnection)
//                    break
//                } catch (e: Exception) {
//                    // Calculate next delay with exponential backoff
//                    currentDelay = (currentDelay * config.multiplier).toLong().coerceAtMost(config.maxDelay)
//
//                    // Emit reconnection failed event
//                    client.events.raise("WebRTCReconnectionFailed", ReconnectInfo(attempts, config.maxAttempts))
//                }
//            }
//
//            if (attempts >= config.maxAttempts) {
//                // Emit max attempts reached event
//                client.events.raise("WebRTCMaxReconnectAttemptsReached", Unit)
//            }
//        }
//    }
//
//    /**
//     * Information about reconnection attempt.
//     */
//    public data class ReconnectInfo(
//        val attempt: Int,
//        val maxAttempts: Int
//    )
//}
