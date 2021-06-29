/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("FunctionName")

package io.ktor.http.content

import io.ktor.util.date.*
import java.nio.file.attribute.*
import java.time.*

/**
 * Construct [LastModifiedVersion] version from a [ZonedDateTime] instance
 */
public fun LastModifiedVersion(lastModified: ZonedDateTime): LastModifiedVersion =
    LastModifiedVersion(lastModified.toGMTDate())

/**
 * Construct [LastModifiedVersion] version from a [FileTime] instance
 */
public fun LastModifiedVersion(lastModified: FileTime): LastModifiedVersion =
    LastModifiedVersion(GMTDate(lastModified.toMillis()))
