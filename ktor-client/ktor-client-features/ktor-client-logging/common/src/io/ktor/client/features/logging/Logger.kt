/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.logging


/**
 * [HttpClient] Logger.
 */
interface Logger {
    /**
     * Add [message] to log.
     */
    fun log(message: String)

    companion object
}

/**
 * Default logger to use.
 */
expect val Logger.Companion.DEFAULT: Logger

/**
 * [Logger] using [println].
 */
val Logger.Companion.SIMPLE: Logger get() = SimpleLogger()

/**
 * Empty [Logger] for test purpose.
 */
val Logger.Companion.EMPTY: Logger
    get() = object : Logger {
        override fun log(message: String) {}
    }

private class SimpleLogger : Logger {
    override fun log(message: String) {
        println("HttpClient: $message")
    }
}
