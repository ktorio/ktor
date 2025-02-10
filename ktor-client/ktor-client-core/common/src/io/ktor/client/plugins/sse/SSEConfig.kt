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
     * Note: this parameter is not supported for some engines.
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
}
