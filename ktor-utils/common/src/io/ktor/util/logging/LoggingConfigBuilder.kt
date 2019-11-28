/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import io.ktor.util.*

/**
 * Configuration builder producing [Config].
 */
class LoggingConfigBuilder() {
    private val slots = ArrayList<LogAttributeKey<*>>()

    private val enhancers = ArrayList<LogRecord.(Config) -> Unit>()
    private val filters = ArrayList<LogRecord.(Config) -> Unit>()
    private val labels = ArrayList<Appendable.(LogRecord) -> Unit>()

    private val appenderList = ArrayList<Appender>()

    constructor(config: Config) : this() {
        slots.addAll(config.keys)
        enhancers.addAll(config.enhancers)
        filters.addAll(config.filters)
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
    @KtorExperimentalAPI
    fun registerKey(key: LogAttributeKey<*>) {
        check(!key.registered) { "The key is already registered." }
        slots += key
        key.index = slots.lastIndex
    }

    /**
     * Add an [interceptor] that may enrich or filter log record.
     */
    fun enrich(interceptor: LogRecord.(Config) -> Unit) {
        enhancers.add(interceptor)
    }

    /**
     * Add a [predicate] that may enrich or filter log record. Filters are always invoked after all enrich blocks.
     */
    fun filter(predicate: LogRecord.(Config) -> Unit) {
        filters.add(predicate)
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
        ArrayList(enhancers),
        ArrayList(filters),
        ArrayList(labels),
        appenderList.combine()
    )
}
