/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

public expect interface Logger {
    public fun error(message: String)
    public fun error(message: String, cause: Throwable)
    public fun warn(message: String)
    public fun warn(message: String, cause: Throwable)
    public fun info(message: String)
    public fun info(message: String, cause: Throwable)
    public fun debug(message: String)
    public fun debug(message: String, cause: Throwable)
    public fun trace(message: String)
    public fun trace(message: String, cause: Throwable)
}

public expect val Logger.isTraceEnabled: Boolean

/**
 * Logs an error from an [exception] using its message
 */
public fun Logger.error(exception: Throwable) {
    error(exception.message ?: "Exception of type ${exception::class}", exception)
}

/**
 * Check `isTraceEnabled` flag before logging to save some memory allocations.
 */
public inline fun Logger.trace(message: () -> String) {
    if (isTraceEnabled) trace(message())
}
