/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import io.ktor.utils.io.pool.*
import kotlin.native.concurrent.*

/**
 * Logger configuration (immutable).
 * @property keys for registered custom record fields
 * @property appender to send records after processing.
 */
class Config(
    val keys: List<LogAttributeKey<*>>,
    internal val enhancers: List<LogRecord.(Config) -> Unit>,
    internal val filters: List<LogRecord.(Config) -> Unit>,
    internal val labels: List<Appendable.(LogRecord) -> Unit>,
    val appender: Appender?
) {
    private val pool: ObjectPool<LogRecord> = pool(this)

    internal val interceptors: List<LogRecord.(Config) -> Unit> = enhancers + filters

    /**
     * Creates [LoggingConfigBuilder] with all parameters copied.
     */
    fun copy(): LoggingConfigBuilder = LoggingConfigBuilder(this)

    internal fun createBuilder(): LogRecord {
        return pool.borrow()
    }

    internal fun recycleBuilder(record: LogRecord) {
        record.discard()
        pool.recycle(record)
    }

    internal fun resetBuilder(record: LogRecord) {
        record.reset()
        for (index in keys.indices) {
            record.reset(keys[index])
        }
    }

    internal fun pass(message: LogRecord) {
        val appender = appender

        if (appender == null) {
            message.discard()
            return
        }

        if (interceptors.isNotEmpty()) {
            for (interceptor in interceptors) {
                interceptor(message, this)
                if (message.discarded) return
            }
        }

        appender.append(message)
    }

    companion object {
        /**
         * The default logging configuration.
         */
        @SharedImmutable
        val Default: Config = LoggingConfigBuilder().apply {
            label { message ->
                append(message.level.name)
            }
        }.build()
    }
}

internal expect fun pool(config: Config): ObjectPool<LogRecord>

internal class LogRecordPool(private val config: Config) : DefaultPool<LogRecord>(100) {
    override fun produceInstance(): LogRecord =
        LogRecord(config)

    override fun clearInstance(instance: LogRecord): LogRecord {
        config.resetBuilder(instance)
        return instance
    }
}

