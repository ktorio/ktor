/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import io.ktor.utils.io.pool.*

internal actual fun pool(config: Config): ObjectPool<LogRecord> = NoPoolRecordPool(config)

private class NoPoolRecordPool(private val config: Config) : NoPoolImpl<LogRecord>() {
    override fun borrow(): LogRecord {
        return LogRecord(config)
    }
}
