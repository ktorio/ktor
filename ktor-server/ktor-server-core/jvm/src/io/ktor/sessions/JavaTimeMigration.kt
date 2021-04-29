/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.sessions

import java.time.*
import java.time.temporal.*

@Suppress("unused", "EXTENSION_SHADOWED_BY_MEMBER")
@Deprecated("Use maxAgeInSeconds or maxAgeDuration instead.")
public var CookieConfiguration.duration: TemporalAmount?
    get() = Duration.ofSeconds(maxAgeInSeconds)
    set(newMaxAge) {
        maxAgeInSeconds = when (newMaxAge) {
            null -> 0
            is Duration -> newMaxAge.toMillis() / 1000L
            else -> newMaxAge[ChronoUnit.SECONDS]
        }
    }
