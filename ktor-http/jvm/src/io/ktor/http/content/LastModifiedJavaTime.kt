@file:Suppress("FunctionName")

package io.ktor.http.content

import io.ktor.util.date.*
import java.nio.file.attribute.*
import java.time.*

/**
 * Construct [LastModifiedVersion] version from a [ZonedDateTime] instance
 */
fun LastModifiedVersion(lastModified: ZonedDateTime) : LastModifiedVersion = LastModifiedVersion(lastModified.toGMTDate())

/**
 * Construct [LastModifiedVersion] version from a [FileTime] instance
 */
fun LastModifiedVersion(lastModified: FileTime) : LastModifiedVersion = LastModifiedVersion(GMTDate(lastModified.toMillis()))

