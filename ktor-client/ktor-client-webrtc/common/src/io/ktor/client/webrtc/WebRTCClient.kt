/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.*

public interface WebRTCClientEngineFactory<out T : WebRTCConfig> {
    public fun create(block: T.() -> Unit = {}): WebRTCEngine
}

/**
 * A multiplatform WebRTC client for establishing peer-to-peer connections,
 * managing media streams, and handling signaling.
 *
 * # Creating a client
 * ```kotlin
 * val client = WebRTCClient()
 * ```
 *
 * # Creating with specific engine
 * ```kotlin
 * val client = WebRTCClient(WebRTCJsEngine())
 * ```
 *
 * # Closing the client
 * ```kotlin
 * client.close()
 * ```
 */
public class WebRTCClient(public val engine: WebRTCEngine) : WebRTCEngine by engine {
    /**
     * The underlying WebRTCClientEngine instance.
     */
    public val engineConfig: WebRTCConfig = engine.config
}

/**
 * Creates a [WebRTCClient] with a specified engine.
 *
 * @param block configuration block for the client
 */
@KtorDsl
public fun <T : WebRTCConfig> WebRTCClient(
    factory: WebRTCClientEngineFactory<T>,
    block: WebRTCConfig.() -> Unit = {}
): WebRTCClient = WebRTCClient(factory.create(block))
