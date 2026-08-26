/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A config for the [SSE] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSEConfig)
 */
public class SSEConfig {
    internal var showCommentEvents = false
    internal var showRetryEvents = false

    /**
     * The reconnection time. If the connection to the server is lost,
     * the client will wait for the specified time before attempting to reconnect.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSEConfig.reconnectionTime)
     */
    public var reconnectionTime: Duration = 3000.milliseconds

    /**
     * The maximum amount of retries to perform for a reconnection request.
     * To enable reconnection, set this value to a number greater than 0.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSEConfig.maxReconnectionAttempts)
     */
    public var maxReconnectionAttempts: Int = 0

    /**
     * Adds events consisting only of comments in the incoming flow.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSEConfig.showCommentEvents)
     */
    public fun showCommentEvents() {
        showCommentEvents = true
    }

    /**
     * Adds events consisting only of the retry field in the incoming flow.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSEConfig.showRetryEvents)
     */
    public fun showRetryEvents() {
        showRetryEvents = true
    }

    /**
     * Controls how the plugin captures a diagnostic buffer of the SSE stream that has already been
     * processed, so you can inspect it when an exception occurs.
     *
     * The buffer is built from bytes the SSE reader has already read, it does not re-read the network.
     *
     * Variants:
     * - [SSEBufferPolicy.Off] — capture is disabled (default).
     * - [SSEBufferPolicy.LastLines] — keeps the last N text lines of the stream.
     * - [SSEBufferPolicy.LastEvent] — keeps the last completed SSE event.
     * - [SSEBufferPolicy.LastEvents] — keeps the last K completed SSE events.
     * - [SSEBufferPolicy.All] — keeps everything that has been read so far. Please note that this may consume a lot of memory.
     *
     * Notes:
     * - This policy applies to failures after the SSE stream has started (e.g., parsing errors or exceptions
     *   thrown inside your `client.sse { ... }` block). It does not affect "handshake" failures
     *   (non-2xx status or non-`text/event-stream`); those are handled separately.
     * - The buffer reflects only what has already been consumed by the SSE parser at the moment of failure.
     * - You can override the global policy per call via the `bufferPolicy` parameter of `client.sse(...)`.
     *
     * Usage:
     * ```
     * install(SSE) {
     *     bufferPolicy = SSEBufferPolicy.LastEvents(5)
     * }
     *
     * try {
     *     client.sse("https://example.com/sse") {
     *         incoming.collect { /* ... */ }
     *     }
     * } catch (e: SSEClientException) {
     *     val text = e.response?.bodyAsText() // contains the last 5 events received
     *     println(text)
     * }
     * ```
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSEConfig.bufferPolicy)
     */
    public var bufferPolicy: SSEBufferPolicy = SSEBufferPolicy.Off
}
