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

private class ThreadNameKey : LogAttributeKey<String>("thread-name", "")
