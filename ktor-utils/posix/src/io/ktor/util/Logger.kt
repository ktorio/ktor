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
    public actual fun getLogger(name: String): Logger = PrintLogger(name)
}

private class PrintLogger(val name: String) : Logger {
    private fun log(level: String, message: String) {
        println("[$level] ($name): $message")
    }

    private fun log(level: String, message: String, cause: Throwable) {
        println("[$level] ($name): $message. Cause: ${cause.stackTraceToString()}")
    }

    override fun error(message: String) {
        log("error", message)
    }

    override fun error(message: String, cause: Throwable) {
        log("error", message, cause)
    }

    override fun warn(message: String) {
        log("warn", message)
    }

    override fun warn(message: String, cause: Throwable) {
        log("warn", message, cause)
    }

    override fun info(message: String) {
        log("info", message)
    }

    override fun info(message: String, cause: Throwable) {
        log("info", message, cause)
    }

    override fun debug(message: String) {
        log("debug", message)
    }

    override fun debug(message: String, cause: Throwable) {
        log("debug", message, cause)
    }

    override fun trace(message: String) {
        log("trace", message)
    }

    override fun trace(message: String, cause: Throwable) {
        log("trace", message, cause)
    }
}
