/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import java.time.*
import java.util.*

/**
 * Creates [LocalDateTime] from this [Date]
 */
@InternalAPI
public fun Date.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(toInstant(), ZoneId.systemDefault())

/**
 * Creates [ZonedDateTime] from this [Date]
 */
@Suppress("DEPRECATION")
@InternalAPI
public fun Date.toZonedDateTime(): ZonedDateTime = ZonedDateTime.ofInstant(toInstant(), GreenwichMeanTime)

/**
 * [ZoneId] for GMT
 */
@InternalAPI
public val GreenwichMeanTime: ZoneId = ZoneId.of("GMT")
