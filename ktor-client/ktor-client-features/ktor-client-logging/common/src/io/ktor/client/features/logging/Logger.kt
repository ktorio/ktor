/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.logging


/**
 * [HttpClient] Logger.
 */
public interface Logger {
    /**
     * Add [message] to log.
     */
    public fun log(message: String)

    public companion object
}

/**
 * Default logger to use.
 */
public expect val Logger.Companion.DEFAULT: Logger

/**
 * [Logger] using [println].
 */
public val Logger.Companion.SIMPLE: Logger get() = SimpleLogger()

/**
 * Empty [Logger] for test purpose.
 */
public val Logger.Companion.EMPTY: Logger
    get() = object : Logger {
        override fun log(message: String) {}
    }

private class SimpleLogger : Logger {
    override fun log(message: String) {
        println("HttpClient: $message")
    }
}
