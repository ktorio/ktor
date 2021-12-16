/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName")

package io.ktor.server.http.content

import io.ktor.http.content.*
import io.ktor.util.date.*
import java.nio.file.attribute.*
import java.time.*
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Construct [LastModifiedVersion] version from a [ZonedDateTime] instance
 */
public fun LastModifiedVersion(lastModified: ZonedDateTime): LastModifiedVersion =
    LastModifiedVersion(GMTDate(lastModified.toInstant().nano.nanoseconds))

/**
 * Construct [LastModifiedVersion] version from a [FileTime] instance
 */
public fun LastModifiedVersion(lastModified: FileTime): LastModifiedVersion =
    LastModifiedVersion(GMTDate(lastModified.toInstant().nano.nanoseconds))
