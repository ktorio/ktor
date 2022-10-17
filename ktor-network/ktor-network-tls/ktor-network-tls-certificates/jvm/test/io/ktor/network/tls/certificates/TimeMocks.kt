/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.certificates

import io.mockk.*
import java.time.*
import java.util.*

internal val nowInTests = Instant.parse("2022-10-20T10:00:00Z")

/**
 * Mocks time-related classes so "now" is always equal to the given [instant] during tests.
 *
 * This includes mocking default [Clock]s, [Instant.now], and [Date]s constructed via the no-arg constructor.
 */
internal fun fixCurrentTimeTo(fixedTime: Instant) {
    mockkStatic(Clock::class)
    every { Clock.systemUTC() } returns Clock.fixed(fixedTime, ZoneOffset.UTC)
    every { Clock.systemDefaultZone() } returns Clock.fixed(fixedTime, ZoneId.systemDefault())

    mockkStatic(Instant::class)
    every { Instant.now() } returns fixedTime

    mockkConstructor(Date::class)
    every { constructedWith<Date>().time } returns fixedTime.toEpochMilli()
    every { constructedWith<Date>().toInstant() } returns fixedTime
}
