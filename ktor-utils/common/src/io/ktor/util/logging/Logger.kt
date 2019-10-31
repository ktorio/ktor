/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

/**
 * Entity that provides logging facilities, configured and connected to appender(s).
 */
open class Logger(
    /**
     * Logger configuration: the default config ([Config.Default]) or the config provided at logger construction.
     */
    val config: Config = Config.Default
) {
    /**
     * Start log record preparation
     */
    @PublishedApi
    internal fun begin(level: Level): LogRecord? {
        if (config.appender == null || config.appender.muted) return null

        return config.createRecord().also {
            it.level = level
        }
    }

    /**
     * Commit log record and send it to appender.
     */
    @PublishedApi
    internal fun commit(record: LogRecord) {
        if (config.appender == null || config.appender.muted) return

        processRecord(record)

        if (!record.discarded) {
            config.appender.flush()
        }
    }

    /**
     * Log [message] with INFO level
     */
    @Deprecated(
        "Use info instead.",
        ReplaceWith("info(message)", "io.ktor.util.logging.info")
    )
    fun log(message: String) {
        info(message)
    }

    private fun processRecord(message: LogRecord) {
        val appender = config.appender

        if (appender == null) {
            message.discard()
            return
        }

        val interceptors = config.interceptors
        if (interceptors.isNotEmpty()) {
            for (interceptor in interceptors) {
                interceptor(message, config)
                if (message.discarded) return
            }
        }

        appender.append(message)
    }

    /**
     * Logger's companion for extensions
     */
    companion object {
        /**
         * Logger with the default configuration.
         */
        val Default: Logger by lazy { logger() }

        /**
         * Logger that never logs anything.
         */
        val Muted: Logger = logger {}
    }
}

/**
 * Creates an instance of logger with the default config.
 * @see Config.Default
 */
fun logger(): Logger = Logger()

/**
 * Creates an instance of logger with additional [appender].
 */
fun logger(appender: Appender): Logger {
    val builder = Config.Default.copy()
    builder.addAppender(appender)
    return Logger(builder.build())
}

/**
 * Configures and creates an instance of logger. The default config is not applied.
 */
fun logger(configure: LoggingConfigBuilder.() -> Unit): Logger {
    val config = LoggingConfigBuilder().apply(configure).build()
    return Logger(config)
}

/**
 * Creates an instance of logger with additional [appender] and then applying configuration block.
 */
fun logger(appender: Appender, configure: LoggingConfigBuilder.() -> Unit): Logger {
    val builder = Config.Default.copy()
    builder.addAppender(appender)
    configure(builder)
    return Logger(builder.build())
}

/**
 * Creates an instance of logger with the specified [config].
 */
fun logger(config: Config): Logger = Logger(config)

/**
 * Creates an instance of logger with modified config amended in the [block].
 */
fun Logger.configure(block: LoggingConfigBuilder.() -> Unit): Logger {
    return Logger(config.copy().apply(block).build())
}
