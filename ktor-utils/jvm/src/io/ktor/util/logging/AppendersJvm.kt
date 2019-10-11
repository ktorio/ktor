/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

internal val TextAppender.Companion.systemStreamsAppender: Appender
    get() = SystemStreamsDispatchingAppender(
        TextAppender(
            Appendable::formatLogRecordDefault
        ) { text -> System.out?.println(text) },
        TextAppender(
            Appendable::formatLogRecordDefault
        ) { text -> System.err?.println(text) }
    )

private class SystemStreamsDispatchingAppender(
    private val infoAppender: Appender,
    private val errorAppender: Appender
) : Appender {
    override val muted: Boolean
        get() = infoAppender.muted && errorAppender.muted

    override fun append(record: LogRecord) {
        val dispatched: Appender = when (record.level) {
            Level.TRACE,
            Level.DEBUG,
            Level.INFO -> infoAppender
            Level.WARNING,
            Level.ERROR -> errorAppender
        }

        dispatched.append(record)
    }

    override fun flush() {
        infoAppender.flush()
        errorAppender.flush()
    }
}
