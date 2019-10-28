/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import kotlinx.atomicfu.*
import kotlin.native.concurrent.*

/**
 * Message log level
 */
@Suppress("KDocMissingDocumentation")
enum class Level {
    TRACE,
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

/**
 * Log event builder with all mutable fields.
 * @property config for this message
 */
class LogRecord internal constructor(val config: Config) {
    private val refCount = atomic(1)

    private val keys: Array<Any?> = config.keys.let { keys ->
        when (keys.size) {
            0 -> EmptySlotArray
            else -> Array(keys.size) { index -> keys[index].initial }
        }
    }

    /**
     * Whether the message has been discarded so will not be passed downstream.
     */
    var discarded: Boolean = false
        private set

    /**
     * Log message level
     */
    var level: Level = Level.INFO
        internal set

    /**
     * Text to be logged.
     */
    var text: String = ""

    /**
     * Related cause or `null`
     */
    var exception: Throwable? = null

    /**
     * Discard message. Useful for message filtering or to complete
     */
    fun discard() {
        discarded = true
    }

    /**
     * Capture an instance of [LogRecord] to use outside of the logging pipeline.
     */
    fun defer(): ManuallyManagedReference {
        refCount.update { old ->
            when (old) {
                0 -> throw IllegalStateException("Already recycled.")
                else -> old + 1
            }
        }
        return ManuallyManagedReference()
    }

    /**
     * Returns a value of custom field registered via [LoggingConfigBuilder.registerKey].
     * @throws IllegalStateException if the [key] wasn't registered before using.
     */
    operator fun <T> get(key: LogAttributeKey<T>): T {
        @Suppress("UNCHECKED_CAST")
        return keys[key.index] as T
    }

    /**
     * Assign a [value] to a custom field identified by the [key] registered via [LoggingConfigBuilder.registerKey].
     * @throws IllegalStateException if the [key] wasn't registered before using.
     */
    operator fun <T> set(key: LogAttributeKey<T>, value: T) {
        keys[key.index] = value
    }

    @PublishedApi
    internal fun release() {
        val decremented = refCount.updateAndGet { old ->
            when (old) {
                0 -> throw IllegalStateException("Already recycled")
                else -> old - 1
            }
        }

        if (decremented == 0) {
            recycle()
        }
    }

    private fun recycle() {
        config.recycleRecord(this)
    }

    internal fun reset(key: LogAttributeKey<*>) {
        keys[key.index] = key.initial
    }

    internal fun reset() {
        check(refCount.compareAndSet(0, 1)) { "Object is in use." }
        discarded = false
        level = Level.INFO
        text = ""
        exception = null
        keys.fill(null)
    }

    /**
     * A box containing a reference to [LogRecord] that could be extracted only once by invoking [consume] function.
     * Useful to capture an instance of [LogRecord] outside of the main logging pipeline.
     */
    inner class ManuallyManagedReference internal constructor() {
        // 0 - initial, 1 - started, 2 - finished
        private val state = atomic(0)

        @PublishedApi
        internal fun start(): LogRecord {
            check(state.compareAndSet(0, 1)) { "Already used." }
            return this@LogRecord
        }

        @PublishedApi
        internal fun end(log: LogRecord) {
            require(log === this@LogRecord)
            check(state.compareAndSet(1, 2)) { "Already used." }
            release()
        }

        /**
         * Use the captured [LogRecord] instance. Can be invoked only once.
         * Provided instance of [LogRecord] shouldn't be captured outside of the [block].
         */
        inline fun consume(block: (LogRecord) -> Unit) {
            val event = start()
            try {
                block(event)
            } finally {
                end(event)
            }
        }
    }
}

@SharedImmutable
private val EmptySlotArray: Array<Any?> = emptyArray()

