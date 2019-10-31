/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

/**
 * Appender that duplicates all records to the [first] and the [second] appender.
 */
internal class TeeAppender(val first: Appender, val second: Appender) :
    Appender {
    override val muted: Boolean
        get() = first.muted && second.muted

    override fun append(record: LogRecord) {
        first.append(record)
        second.append(record)
    }

    override fun flush() {
        first.flush()
        second.flush()
    }
}
