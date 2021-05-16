/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

@InternalAPI
public expect enum class LoggingLevel {
    ERROR, WARN, INFO, DEBUG, TRACE
}

@InternalAPI
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

@InternalAPI
public expect object LoggerFactory {
    public fun getLogger(name: String): Logger
}
