/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import org.slf4j.*

/**
 * Appends to Slf4j logger.
 */
class Slf4jAppender : Appender {
    private val logger = LoggerFactory.getLogger("logger")

    override fun append(record: LogRecord) {
        val text = buildString {
            formatLogRecordDefault(record)
        }

        when (record.level) {
            Level.TRACE -> logger.trace(text, record.exception)
            Level.DEBUG -> logger.debug(text, record.exception)
            Level.INFO -> logger.info(text, record.exception)
            Level.WARNING -> logger.warn(text, record.exception)
            Level.ERROR -> logger.error(text, record.exception)
        }
    }

    override fun flush() {
    }
}
