/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import io.ktor.util.*
import io.ktor.utils.io.pool.*
import kotlin.native.concurrent.*

/**
 * Logger configuration (immutable).
 * @property keys for registered custom record fields
 * @property appender to send records after processing.
 */
class Config internal constructor(
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

    internal fun createRecord(): LogRecord {
        return pool.borrow()
    }

    internal fun recycleRecord(record: LogRecord) {
        record.discard()
        pool.recycle(record)
    }

    internal fun resetRecord(record: LogRecord) {
        record.reset()
        for (index in keys.indices) {
            record.reset(keys[index])
        }
    }

    companion object {
        // NOTE: Empty should be before Default because the initialization order matters.
        /**
         * Empty logger config with no labels, no appenders.
         */
        @SharedImmutable
        val Empty: Config = LoggingConfigBuilder().build()

        /**
         * The default logging configuration.
         */
        @SharedImmutable
        val Default: Config = LoggingConfigBuilder().apply {
            label { message ->
                append(message.level.name)
            }
            defaultPlatformConfig()
        }.build()
    }
}

/**
 * Creates an empty config with the single [appender].
 */
@Suppress("FunctionName")
fun Config(appender: Appender): Config = Config.Empty.withAppender(appender)

/**
 * Creates a new logger config with additional [appender].
 */
fun Config.withAppender(appender: Appender): Config = LoggingConfigBuilder(this).apply {
    addAppender(appender)
}.build()

/**
 * Search for the last log key of type [K] or `null` if not registered.
 */
@KtorExperimentalAPI
inline fun <reified K : LogAttributeKey<*>> Config.findKey(): K? {
    val keys = keys
    for (index in keys.lastIndex downTo 0) {
        if (keys[index] is K) {
            return keys[index] as K
        }
    }

    return null
}

internal expect fun pool(config: Config): ObjectPool<LogRecord>

internal class LogRecordPool(private val config: Config) : DefaultPool<LogRecord>(100) {
    override fun produceInstance(): LogRecord =
        LogRecord(config)

    override fun clearInstance(instance: LogRecord): LogRecord {
        config.resetRecord(instance)
        return instance
    }
}

internal expect fun LoggingConfigBuilder.defaultPlatformConfig()

