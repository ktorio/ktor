/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.client.features.logging

import io.ktor.util.logging.*

/**
 * [HttpClient] Logger.
 */
@Deprecated(
    "Use ktor utils Logger instead.",
    ReplaceWith("Logger", "io.ktor.util.logging.Logger")
)
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
@Deprecated(
    "Use ktor utils Logger.Default instead.",
    ReplaceWith("Logger.Default", "io.ktor.util.logging.Logger.Default")
)
expect val Logger.Companion.DEFAULT: Logger

/**
 * [Logger] using [println].
 */
@Deprecated(
    "Use ktor utils Logger.Default instead.",
    ReplaceWith("Logger.Default", "io.ktor.util.logging.Logger.Default")
)
val Logger.Companion.SIMPLE: Logger get() = AdapterLogger(logger())

/**
 * Empty [Logger] for test purpose.
 */
@Deprecated(
    "Use ktor utils Logger.Muted instead.",
    ReplaceWith("Logger.Muted", "io.ktor.util.logging.Logger.Muted")
)
val Logger.Companion.EMPTY: Logger
    get() = object : Logger {
        override fun log(message: String) {}
    }

internal class AdapterLogger(val logger: io.ktor.util.logging.Logger) : Logger {
    override fun log(message: String) {
        logger.info(message)
    }
}

@Suppress("FunctionName")
internal fun AdapterLogger(delegate: Logger) : io.ktor.util.logging.Logger {
    val appender = TextAppender { delegate.log(it.toString()) }
    return logger(appender = appender)
}
