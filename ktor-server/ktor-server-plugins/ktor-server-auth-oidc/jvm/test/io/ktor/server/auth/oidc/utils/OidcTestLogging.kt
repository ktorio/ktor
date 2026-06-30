/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc.utils

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

internal class CapturedLogEvents(
    private val logger: ch.qos.logback.classic.Logger,
    level: Level,
) : AutoCloseable {
    private val previousLevel = logger.level
    private val appender = ListAppender<ILoggingEvent>().apply { start() }

    val events: List<ILoggingEvent>
        get() = appender.list

    init {
        logger.level = level
        logger.addAppender(appender)
    }

    override fun close() {
        logger.detachAppender(appender)
        logger.level = previousLevel
    }
}

internal fun captureProviderLogs(providerName: String, level: Level): CapturedLogEvents {
    val name = "io.ktor.server.auth.oidc.OidcProvider[$providerName]"
    val logger = LoggerFactory.getLogger(name) as ch.qos.logback.classic.Logger
    return CapturedLogEvents(logger, level)
}
