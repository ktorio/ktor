/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.logging

import io.ktor.client.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.logging.labels.*

@Suppress("DEPRECATION", "KDocMissingDocumentation")
@Deprecated(
    "Use ktor utils Logger.Default instead.",
    ReplaceWith("Logger.Default", "io.ktor.util.logging.Logger.Default")
)
actual val Logger.Companion.DEFAULT: Logger
    get() = object : Logger {
        private val delegate = logger().forClass<HttpClient>()
        override fun log(message: String) {
            delegate.info(message)
        }
    }

/**
 * Android [Logger]: breaks up long log messages that would be truncated by Android's max log
 * length of 4068 characters
 */
@Suppress("DEPRECATION")
@KtorExperimentalAPI
@Deprecated(
    "Use Logger.Android instead.", ReplaceWith(
        "Logger.Android",
        "io.ktor.client.features.logging.AdapterLogger"
    )
)
val Logger.Companion.ANDROID: Logger
    get() = AdapterLogger(io.ktor.util.logging.Logger.Android)

/**
 * Android [Logger]: breaks up long log messages that would be truncated by Android's max log
 * length of 4068 characters
 */
val io.ktor.util.logging.Logger.Companion.Android: io.ktor.util.logging.Logger
    get() = logger(MessageLengthLimitingAppender(delegate = Appender.Default))

/**
 * A [Logger] that breaks up log messages into multiple logs no longer than [maxLength]
 * @property maxLength max length allowed for a log message
 * @property minLength if log message is longer than [maxLength], attempt to break the log
 * message at a new line between [minLength] and [maxLength] if one exists
 */
@Suppress("DEPRECATION")
@KtorExperimentalAPI
class MessageLengthLimitingLogger(
    private val maxLength: Int = 4000,
    private val minLength: Int = 3000,
    private val delegate: Logger = Logger.DEFAULT
) : Logger {
    override fun log(message: String) {
        logLong(message, minLength, maxLength) {
            delegate.log(it)
        }
    }
}

/**
 * A [Logger] that breaks up log messages into multiple logs no longer than [maxLength]
 * @property maxLength max length allowed for a log message
 * @property minLength if log message is longer than [maxLength], attempt to break the log
 * message at a new line between [minLength] and [maxLength] if one exists
 */
@KtorExperimentalAPI
class MessageLengthLimitingAppender(
    private val maxLength: Int = 4000,
    private val minLength: Int = 3000,
    private val delegate: Appender
) : Appender {
    private val lines = ArrayList<String>()
    private val inner = TextAppender { lines.add(it.toString()) }

    override fun append(record: LogRecord) {
        inner.append(record)
    }

    override fun flush() {
        inner.flush()

        if (lines.isNotEmpty()) {
            lines.forEach {
                delegate.append(LogRecord.createSimple().apply {
                    text = it
                })
            }
            delegate.flush()
        }
    }
}

private tailrec fun logLong(message: String, minLength: Int, maxLength: Int, delegate: (String) -> Unit) {
    // String to be logged is longer than the max...
    if (message.length > maxLength) {
        var msgSubstring = message.substring(0, maxLength)
        var msgSubstringEndIndex = maxLength

        // Try to find a substring break at a newline char.
        msgSubstring.lastIndexOf('\n').let { lastIndex ->
            if (lastIndex >= minLength) {
                msgSubstring = msgSubstring.substring(0, lastIndex)
                //skip over new line char
                msgSubstringEndIndex = lastIndex + 1
            }
        }

        // Log the substring.
        delegate(msgSubstring)

        // Recursively log the remainder.
        logLong(message.substring(msgSubstringEndIndex), minLength, maxLength, delegate)
    } else {
        // String to be logged is shorter than the max...
        delegate(message)
    }
}
