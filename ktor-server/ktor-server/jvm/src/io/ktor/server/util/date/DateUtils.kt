/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.util.date

import io.ktor.util.date.*
import java.time.*
import java.util.concurrent.*

/**
 * Convert [Instant] to [GMTDate]
 */
public fun Instant.toGMTDate(): GMTDate =
    GMTDate(TimeUnit.SECONDS.toMillis(atZone(ZoneOffset.UTC).toEpochSecond()))

/**
 * Convert [ZonedDateTime] to [GMTDate]
 */
public fun ZonedDateTime.toGMTDate(): GMTDate = toInstant().toGMTDate()
