/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import kotlin.jvm.*

/**
 * Text format appender useful for sending to text destinations such as files and terminals.
 * This is always buffered so does send already formatted and concatenated texts to the [output].
 *
 * @property formatter format function to convert records to text.
 * @property output to print formatted and concatenated records to.
 *
 * @see formatLogRecordDefault
 */
class TextAppender(
    val formatter: Appendable.(LogRecord) -> Unit,
    val output: (CharSequence) -> Unit
) : Appender {
    private val buffer = StringBuilder()

    constructor(output: (CharSequence) -> Unit) : this(Appendable::formatLogRecordDefault, output)

    @Synchronized
    override fun append(record: LogRecord) {
        if (buffer.isNotEmpty()) {
            buffer.append('\n')
        }
        buffer.formatter(record)
        if (buffer.length > 4096) {
            flush()
        }
    }

    @Synchronized
    override fun flush() {
        if (buffer.isNotEmpty()) {
            try {
                output(buffer)
            } finally {
                buffer.clear()
            }
        }
    }

    companion object {
        internal val PrintlnAppender: TextAppender
            get() = TextAppender(Appendable::formatLogRecordDefault) { buffer ->
                println(buffer)
            }
    }
}
