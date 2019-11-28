/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import io.ktor.util.*

/**
 * The default log record format.
 */
@KtorExperimentalAPI
fun Appendable.formatLogRecordDefault(message: LogRecord) {
    appendMarkers(message)

    append(message.text)

    message.exception?.let { exception ->
        append('\n')
        appendException(exception)
    }
}

private fun Appendable.appendException(exception: Throwable) {
    append(exception.toString())

    val cause = exception.cause
    if (cause != null && cause !== exception) {
        appendExceptionTrace(exception, cause)
    } else {
        append('\n')
    }
}

private fun Appendable.appendExceptionTrace(parent: Throwable, exception: Throwable) {
    val visited = ArrayList<Throwable>()
    visited += parent
    visited += exception

    var current = exception
    do {
        append("\nCaused by: ")
        append(current.toString())

        current = current.cause ?: break
        if (current in visited) break
    } while (true)

    append('\n')
}

private fun Appendable.appendMarkers(message: LogRecord) {
    val labels = message.config.labels
    if (labels.isEmpty()) return

    append('[')
    labels[0](message)

    for (index in 1 until labels.size) {
        append("] [")
        labels[index](message)
    }

    append("] ")
}
