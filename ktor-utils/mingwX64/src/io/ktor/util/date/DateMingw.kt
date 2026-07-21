/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import kotlinx.cinterop.*
import platform.posix.*
import platform.windows.*

@OptIn(ExperimentalForeignApi::class)
@Suppress("FunctionName")
internal actual fun system_time(tm: CValuesRef<tm>?): Long {
    return _mkgmtime(tm).convert()
}

// Number of 100-nanosecond intervals between the Windows FILETIME epoch (1601-01-01 UTC)
// and the Unix epoch (1970-01-01 UTC).
private const val EPOCH_DIFFERENCE_100NS: ULong = 116444736000000000UL

/**
 * Gets current system time in milliseconds since a certain moment in the past,
 * only delta between two subsequent calls makes sense.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.getTimeMillis)
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun getTimeMillis(): Long = memScoped {
    val timeHolder = alloc<FILETIME>()
    GetSystemTimeAsFileTime(timeHolder.ptr)
    val time = alloc<ULARGE_INTEGER>()
    time.HighPart = timeHolder.dwHighDateTime
    time.LowPart = timeHolder.dwLowDateTime
    ((time.QuadPart - EPOCH_DIFFERENCE_100NS) / 10000U).toLong()
}
