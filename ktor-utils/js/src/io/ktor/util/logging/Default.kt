/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

/**
 * Appender for the javascript [console].
 */
class ConsoleAppender : Appender {
    override fun append(record: LogRecord) {
        val exception = record.exception
        record.exception = null

        val formatted = buildString {
            formatLogRecordDefault(record)
        }

        console.log(formatted, exception)
    }

    override fun flush() {
    }
}

/**
 * The default platform appender. Usually it prints to stdout.
 */
actual val Appender.Default: Appender
    get() = ConsoleAppender()

