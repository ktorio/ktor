/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.ratelimit

import io.ktor.client.plugins.api.*
import io.ktor.ratelimit.*
import io.ktor.utils.io.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for the client [RateLimit] plugin.
 */
@KtorDsl
public class RateLimitConfig {
    /**
     * Limiter shared by all outgoing attempts made by this client, including retries and redirects.
     *
     * Defaults to an initially full bucket of ten tokens, replenishing one token every second.
     */
    @OptIn(kotlin.time.ExperimentalTime::class)
    public var rateLimiter: RateLimiter = RateLimiter.incrementalRefill(limit = 10, refillInterval = 1.seconds)

    /**
     * Maximum time a request waits for tokens, including its time behind other requests. Defaults to five seconds.
     *
     * Must be positive. Use [Duration.INFINITE] to wait until tokens are available or the request is cancelled.
     * Expiration fails with [RateLimitWaitTimeoutException].
     */
    public var maxWaitTime: Duration = 5.seconds
}

/**
 * Limits outgoing request attempts, suspending requests in FIFO order until tokens are available.
 *
 * The queue is unbounded by default and each request waits for at most five seconds.
 * Waiting does not occupy an engine connection, and cancelling a request removes it from the queue.
 * Closing the client cancels waiting requests. Completing an HTTP request does not return consumed tokens.
 */
@OptIn(InternalAPI::class)
public val RateLimit: ClientPlugin<RateLimitConfig> = createClientPlugin("RateLimit", ::RateLimitConfig) {
    val queue = RateLimiterQueue(pluginConfig.rateLimiter, pluginConfig.maxWaitTime)

    on(SendingRequest) { request, _ ->
        coroutineScope {
            val admission = coroutineContext.job
            val cancellation = request.executionContext.invokeOnCompletion { cause ->
                if (cause != null) {
                    admission.cancel("HTTP request was cancelled while waiting for rate limit tokens", cause)
                }
            }
            try {
                queue.acquire()
            } finally {
                cancellation.dispose()
            }
        }
    }

    onClose {
        queue.close()
    }
}
