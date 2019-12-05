/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.features

import io.ktor.sessions.*
import java.time.*

@Suppress("unused", "EXTENSION_SHADOWED_BY_MEMBER")
@Deprecated("Use maxAgeInSeconds or maxAgeDuration instead.")
var CORS.Configuration.maxAge: Duration
    get() = Duration.ofSeconds(maxAgeInSeconds)
    set(newMaxAge) {
        maxAgeInSeconds = newMaxAge.toMillis() / 1000
    }

@Suppress("unused", "EXTENSION_SHADOWED_BY_MEMBER")
@Deprecated("Use maxAgeInSeconds or maxAgeDuration instead.")
var HSTS.Configuration.maxAge: Duration
    get() = Duration.ofSeconds(maxAgeInSeconds)
    set(newMaxAge) {
        maxAgeInSeconds = newMaxAge.toMillis() / 1000
    }
