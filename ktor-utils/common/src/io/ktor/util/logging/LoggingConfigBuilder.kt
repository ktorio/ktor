/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

/**
 * Configuration builder producing [Config].
 */
class LoggingConfigBuilder() {
    private val slots = ArrayList<LogAttributeKey<*>>()

    private val interceptors = ArrayList<LogRecord.(Config) -> Unit>()
    private val labels = ArrayList<Appendable.(LogRecord) -> Unit>()

    private val appenderList = ArrayList<Appender>()

    constructor(config: Config) : this() {
        slots.addAll(config.keys)
        interceptors.addAll(config.interceptors)
        labels.addAll(config.labels)
        config.appender?.let { appenderList.add(it) }
    }

    /**
     * Registered record fields keys.
     */
    val keys: List<LogAttributeKey<*>> get() = slots

    /**
     * Register a new field identified by the [key].
     */
    fun registerKey(key: LogAttributeKey<*>) {
        check(!key.registered) { "The key is already registered." }
        slots += key
        key.index = slots.lastIndex
    }

    /**
     * Add an [interceptor] that may enrich or filter log record.
     */
    fun enrich(interceptor: LogRecord.(Config) -> Unit) {
        interceptors.add(interceptor)
    }

    /**
     * Add a label that is added for every log record.
     */
    fun label(format: Appendable.(LogRecord) -> Unit) {
        labels.add(format)
    }

    /**
     * Adds [appender] for logging.
     */
    fun addAppender(appender: Appender) {
        appenderList += appender
    }

    /**
     * Build an instance of [Config].
     */
    fun build(): Config = Config(
        ArrayList(slots),
        ArrayList(interceptors),
        ArrayList(labels),
        appenderList.combine()
    )
}
