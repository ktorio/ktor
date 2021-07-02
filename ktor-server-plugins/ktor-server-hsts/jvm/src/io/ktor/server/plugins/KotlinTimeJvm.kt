/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import kotlin.math.*
import kotlin.time.*

@ExperimentalTime
public var HSTS.Configuration.maxAgeDuration: Duration
    get() = maxAgeInSeconds.seconds
    set(newMaxAge) {
        require(!newMaxAge.isNegative()) { "Only non-negative durations can be specified" }
        maxAgeInSeconds = newMaxAge.inSeconds.roundToLong()
    }
