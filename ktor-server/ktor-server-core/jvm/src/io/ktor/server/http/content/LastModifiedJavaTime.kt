/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName")

package io.ktor.server.http.content

import io.ktor.http.content.*
import kotlinx.datetime.*
import java.nio.file.attribute.*
import java.time.*

/**
 * Construct [LastModifiedVersion] version from a [ZonedDateTime] instance
 */
public fun LastModifiedVersion(lastModified: ZonedDateTime): LastModifiedVersion =
    LastModifiedVersion(lastModified.toInstant().toKotlinInstant())

/**
 * Construct [LastModifiedVersion] version from a [FileTime] instance
 */
public fun LastModifiedVersion(lastModified: FileTime): LastModifiedVersion =
    LastModifiedVersion(lastModified.toInstant().toKotlinInstant())

/**
 * Construct [LastModifiedVersion] version from a [Long] representing the seconds since epoch
 */
public fun LastModifiedVersion(lastModified: Long): LastModifiedVersion =
    LastModifiedVersion(kotlinx.datetime.Instant.fromEpochSeconds(lastModified))
