/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

/**
 * Entity that provides logging facilities, configured and connected to appender(s).
 */
interface Logger {
    /**
     * Logger configuration: the default config ([Config.Default]) or the config provided at logger construction.
     */
    val config: Config get() = Config.Default

    /**
     * Start log record preparation
     */
    fun begin(level: Level): LogRecord?

    /**
     * Commit log record and send it to appender.
     */
    fun commit(record: LogRecord)
}

/**
 * Creates an instance of logger with the default config.
 * @see Config.Default
 */
fun logger(): Logger = DefaultLogger()

/**
 * Creates an instance of logger with additional [appender].
 */
fun logger(appender: Appender): Logger {
    val builder = Config.Default.copy()
    builder.addAppender(appender)
    return DefaultLogger(builder.build())
}

/**
 * Configures and creates an instance of logger. The default config is not applied.
 */
fun logger(configure: LoggingConfigBuilder.() -> Unit): Logger {
    val config = LoggingConfigBuilder().apply(configure).build()
    return DefaultLogger(config)
}

/**
 * Creates an instance of logger with the specified [config].
 */
fun logger(config: Config): Logger = DefaultLogger(config)

/**
 * Creates an instance of logger with modified config amended in the [block].
 */
fun Logger.fork(block: LoggingConfigBuilder.() -> Unit): Logger {
    return DefaultLogger(config.copy().apply(block).build())
}

private class DefaultLogger(override val config: Config = Config.Default) : Logger {
    override fun begin(level: Level): LogRecord? {
        if (config.appender == null || config.appender.muted) return null

        return config.createBuilder().also {
            it.level = level
        }
    }

    override fun commit(record: LogRecord) {
        if (config.appender == null || config.appender.muted) return

        config.pass(record)
        if (!record.discarded) {
            config.appender.flush()
        }
    }
}
