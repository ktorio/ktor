/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

/**
 * Exposes the [Date API](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date) to Kotlin.
 */
internal external class Date() {
    public constructor(milliseconds: Double)

    public fun getTime(): Double

    public fun getUTCDate(): Int

    public fun getUTCDay(): Int

    public fun getUTCFullYear(): Int

    public fun getUTCHours(): Int

    public fun getUTCMinutes(): Int

    public fun getUTCMonth(): Int

    public fun getUTCSeconds(): Int

    public companion object {
        public fun UTC(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Double
    }
}
