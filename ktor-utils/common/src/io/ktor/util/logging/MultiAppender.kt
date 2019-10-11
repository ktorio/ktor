/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

internal class MultiAppender(val appenders: Array<Appender>) : Appender {
    init {
        require(appenders.size > 2)
    }

    override val muted: Boolean
        get() = appenders.all { it.muted }

    override fun append(record: LogRecord) {
        for (index in 0 until appenders.size) {
            appenders[index].append(record)
        }
    }

    override fun flush() {
        for (index in 0 until appenders.size) {
            appenders[index].flush()
        }
    }
}
