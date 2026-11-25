/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

internal fun io.ktor.ratelimit.RateLimiter.asServerRateLimiter(): RateLimiter =
    if (this is SharedRateLimiter) delegate else ServerRateLimiter(this)

internal fun RateLimiter.asSharedRateLimiter(): io.ktor.ratelimit.RateLimiter =
    if (this is ServerRateLimiter) delegate else SharedRateLimiter(this)

internal fun io.ktor.ratelimit.RateLimiter.State.asServerState(): RateLimiter.State = when (this) {
    is io.ktor.ratelimit.RateLimiter.State.Available ->
        RateLimiter.State.Available(remainingTokens, limit, refillAtTimeMillis)

    is io.ktor.ratelimit.RateLimiter.State.Exhausted -> RateLimiter.State.Exhausted(toWait)
}

internal fun RateLimiter.State.asSharedState(): io.ktor.ratelimit.RateLimiter.State = when (this) {
    is RateLimiter.State.Available ->
        io.ktor.ratelimit.RateLimiter.State.Available(remainingTokens, limit, refillAtTimeMillis)

    is RateLimiter.State.Exhausted -> io.ktor.ratelimit.RateLimiter.State.Exhausted(toWait)
}

private class ServerRateLimiter(val delegate: io.ktor.ratelimit.RateLimiter) : RateLimiter {
    override suspend fun tryConsume(tokens: Int): RateLimiter.State = delegate.tryConsume(tokens).asServerState()

    override suspend fun snapshot(): io.ktor.ratelimit.RateLimiterSnapshot = delegate.snapshot()
}

private class SharedRateLimiter(val delegate: RateLimiter) : io.ktor.ratelimit.RateLimiter {
    override suspend fun tryConsume(tokens: Int): io.ktor.ratelimit.RateLimiter.State =
        delegate.tryConsume(tokens).asSharedState()

    override suspend fun snapshot(): io.ktor.ratelimit.RateLimiterSnapshot = delegate.snapshot()
}
