/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

/**
 * Entity of this kind is suitable for writing log records to destinations such as files, console
 * or dispatching/delegating. Propagating records to destination could be immediate or delayed due to reasons such as
 * buffering\batching, asynchronous processing and so on.
 */
interface Appender {
    /**
     * An appender could be muted in some cases. There are multiple reasons for muting appenders.
     * It could be muted by a user or muted because of a technical reason such as missing destination.
     * For example, if an output stream is closed or there is no log observers for some reason.
     */
    val muted: Boolean get() = false

    /**
     * Handle logging [record] by printing it to stdout or some other destination.
     * Please note that it may postpone event processing until [flush] invocation or some appender-specific condition.
     * An [record] instance SHOULD NOT be captured outside of this function block.
     * To capture an event use [LogRecord.defer].
     *
     * @see LogRecord.defer
     * @see LogRecord.ManuallyManagedReference
     * @see flush
     */
    fun append(record: LogRecord)

    /**
     * Trigger propagation for records appended before. It may or may not wait for processing completion.
     */
    fun flush()
}

/**
 * The default platform appender. Usually it prints to stdout.
 */
expect val Appender.Default: Appender

