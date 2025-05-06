/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.certificates

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

internal val nowInTests = Instant.parse("2022-10-20T10:00:00Z")

/**
 * Mocks time-related classes so "now" is always equal to the given [fixedTime] during tests.
 *
 * This includes mocking default [Clock]s and [Instant.now].
 */
internal fun fixCurrentTimeTo(fixedTime: Instant) {
    mockkStatic(Clock::class)
    every { Clock.systemUTC() } returns Clock.fixed(fixedTime, ZoneOffset.UTC)
    every { Clock.systemDefaultZone() } returns Clock.fixed(fixedTime, ZoneId.systemDefault())

    mockkStatic(Instant::class)
    every { Instant.now() } returns fixedTime
}

internal fun unmockCurrentTime() {
    unmockkStatic(Clock::class)
    unmockkStatic(Instant::class)
}
