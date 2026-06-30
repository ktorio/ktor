/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import kotlin.wasm.unsafe.*

/**
 * Create new gmt date from the [timestamp].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.GMTDate)
 *
 * @param timestamp is a number of epoch milliseconds (it is `now` by default).
 */
public actual fun GMTDate(timestamp: Long?): GMTDate = GMTDateImpl(timestamp)

/**
 * Create an instance of [GMTDate] from the specified date/time components
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.GMTDate)
 */
public actual fun GMTDate(seconds: Int, minutes: Int, hours: Int, dayOfMonth: Int, month: Month, year: Int): GMTDate =
    GMTDateImpl(seconds, minutes, hours, dayOfMonth, month, year)

/**
 * Invalid exception: possible overflow or underflow
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.InvalidTimestampException)
 */
public class InvalidTimestampException(timestamp: Long) : IllegalStateException(
    "Invalid date timestamp exception: $timestamp"
)

/**
 * Gets current system time in milliseconds since certain moment in the past, only delta between two subsequent calls makes sense.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.date.getTimeMillis)
 */
@OptIn(UnsafeWasmMemoryApi::class)
public actual fun getTimeMillis(): Long = withScopedMemoryAllocator { allocator ->
    val time = allocator.allocate(8)
    val result = wasiClockTimeGet(0/*REALTIME*/, 1, time.address.toInt())
    check(result == 0) { "WASI error code: $result" }
    time.loadLong() / 1000000L
}

// returns nanos
@OptIn(ExperimentalWasmInterop::class)
@WasmImport("wasi_snapshot_preview1", "clock_time_get")
private external fun wasiClockTimeGet(clockId: Int, precision: Long, resultPtr: Int): Int
