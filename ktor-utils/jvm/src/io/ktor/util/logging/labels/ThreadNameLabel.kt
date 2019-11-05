/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.labels

import io.ktor.util.logging.*

/**
 * Add thread name to log records.
 */
fun LoggingConfigBuilder.threadName() {
    val threadNameKey = ThreadNameKey()

    registerKey(threadNameKey)

    enrich {
        this[threadNameKey] = Thread.currentThread().name
    }

    label { message ->
        append(message[threadNameKey])
    }
}

internal fun LoggingConfigBuilder.ensureThreadName() {
    // TODO
    val threadNameKey = ThreadNameKey()

    registerKey(threadNameKey)

    enrich {
        this[threadNameKey] = Thread.currentThread().name
    }
}

/**
 * Returns record's thread name or `null` of [threadName] wasn't installed.
 */
val LogRecord.threadName: String?
    get() = config.findKey<ThreadNameKey>()?.let { get(it) }

private class ThreadNameKey : LogAttributeKey<String>("thread-name", "")
