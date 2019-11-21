/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import io.ktor.utils.io.pool.*

internal actual fun pool(config: Config): ObjectPool<LogRecord> {
    return LogRecordPool(config)
}

internal actual fun LoggingConfigBuilder.defaultPlatformConfig() {
    try {
        Class.forName("org.slf4j.impl.StaticLoggerBinder", false, javaClass.classLoader)
        addAppender(Slf4jAppender())
    } catch (ignore: Throwable) {
        // TODO: here we can't use logger since this method is invoked during logger initialization
        Appender.Default.apply {
            append(LogRecord.createSimple().apply {
                text = "Unable to locate slf4j logger implementation"
                level = Level.TRACE
                exception = ignore
            })
            flush()
        }
    }
}
