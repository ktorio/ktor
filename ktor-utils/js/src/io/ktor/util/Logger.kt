/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

@InternalAPI
public actual interface Logger {
    public actual fun error(message: String)
    public actual fun error(message: String, cause: Throwable)
    public actual fun warn(message: String)
    public actual fun warn(message: String, cause: Throwable)
    public actual fun info(message: String)
    public actual fun info(message: String, cause: Throwable)
    public actual fun debug(message: String)
    public actual fun debug(message: String, cause: Throwable)
    public actual fun trace(message: String)
    public actual fun trace(message: String, cause: Throwable)
}

@InternalAPI
public actual enum class LoggingLevel {
    ERROR, WARN, INFO, DEBUG, TRACE
}

@InternalAPI
public actual object LoggerFactory {
    public actual fun getLogger(name: String): Logger = ConsoleLogger(name)
}

private class ConsoleLogger(private val name: String) : Logger {
    override fun error(message: String) {
        console.error(name, message)
    }

    override fun error(message: String, cause: Throwable) {
        console.error(name, message, "Cause:", cause.stackTraceToString())
    }

    override fun warn(message: String) {
        console.warn(name, message)
    }

    override fun warn(message: String, cause: Throwable) {
        console.warn(name, message, "Cause:", cause.stackTraceToString())
    }

    override fun info(message: String) {
        console.info(name, message)
    }

    override fun info(message: String, cause: Throwable) {
        console.info(name, message, "Cause:", cause.stackTraceToString())
    }

    override fun debug(message: String) {
        console.log(name, message)
    }

    override fun debug(message: String, cause: Throwable) {
        console.log(name, message, "Cause:", cause.stackTraceToString())
    }

    override fun trace(message: String) {
        console.log(name, message)
    }

    override fun trace(message: String, cause: Throwable) {
        console.log(name, message, "Cause:", cause.stackTraceToString())
    }
}
