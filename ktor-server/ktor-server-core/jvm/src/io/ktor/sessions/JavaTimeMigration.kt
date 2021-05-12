/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.sessions

import java.time.*
import java.time.temporal.*

@Deprecated(
    "Use maxAgeInSeconds or maxAgeDuration instead.",
    level = DeprecationLevel.ERROR
)
public var CookieConfiguration.duration: TemporalAmount?
    get() = Duration.ofSeconds(maxAgeInSeconds)
    set(newMaxAge) {
        maxAgeInSeconds = when (newMaxAge) {
            null -> 0
            is Duration -> newMaxAge.toMillis() / 1000L
            else -> newMaxAge[ChronoUnit.SECONDS]
        }
    }
