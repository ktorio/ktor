/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.util

import io.ktor.util.date.*
import java.time.*
import kotlin.time.Duration.Companion.seconds

/**
 * Convert [Instant] to [GMTDate]
 */
public fun Instant.toGMTDate(): GMTDate =
    GMTDate(durationSinceEpoch = atZone(ZoneOffset.UTC).toEpochSecond().seconds)

/**
 * Convert [ZonedDateTime] to [GMTDate]
 */
public fun ZonedDateTime.toGMTDate(): GMTDate = toInstant().toGMTDate()
