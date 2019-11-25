/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging.rolling

import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.core.*

/**
 * Synchronously encodes log records to the specified [Output].
 * Log records could be encoded in either text or binary format.
 */
@KtorExperimentalAPI
interface RecordEncoder {
    /**
     * Encode [record] to the specified [output]. Shouldn't capture [record].
     */
    fun encode(record: LogRecord, output: Output)

    companion object {
        /**
         * The default record encoder that encodes in the default text format by [formatLogRecordDefault] function.
         */
        val Default: RecordEncoder = object : RecordEncoder {
            override fun encode(record: LogRecord, output: Output) {
                output.writeText(buildString {
                    formatLogRecordDefault(record)
                    append('\n')
                })
            }
        }
    }
}
