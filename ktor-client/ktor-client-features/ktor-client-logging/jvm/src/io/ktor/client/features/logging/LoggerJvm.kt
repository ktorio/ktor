/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.logging

import io.ktor.client.*
import org.slf4j.*

actual val Logger.Companion.DEFAULT: Logger
    get() = object : Logger {
        private val delegate = LoggerFactory.getLogger(HttpClient::class.java)!!
        override fun log(message: String) {
            delegate.info(message)
        }
    }


/**
 * Android [Logger]: breaks up long log messages that would be truncated by Android's max log
 * length of 4068 characters
 */
val Logger.Companion.ANDROID: Logger
    get() = MaxLengthLogger()

/**
 * A [Logger] that breaks up log messages into multiple logs no longer than [MAX_INDEX]
 * @property MAX_INDEX max length allowed for a log message
 * @property MIN_INDEX if log message is longer than [MAX_INDEX], attempt to break the log
 * message at a new line between [MIN_INDEX] and [MAX_INDEX] if one exists
 */
class MaxLengthLogger(private val MAX_INDEX : Int = 4000, private val MIN_INDEX: Int = 3000) : Logger {
    private val delegate = LoggerFactory.getLogger(HttpClient::class.java)!!
    override fun log(message: String) {
        logLong(message)
    }

    private tailrec fun logLong(message: String) {
        // String to be logged is longer than the max...
        if (message.length > MAX_INDEX) {
            var msgSubstring = message.substring(0, MAX_INDEX)
            var msgSubstringEndIndex = MAX_INDEX

            // Try to find a substring break at a newline char.
            msgSubstring.lastIndexOf('\n').let { lastIndex->
                if (lastIndex >= MIN_INDEX) {
                    msgSubstring = msgSubstring.substring(0, lastIndex)
                    //skip over new line char
                    msgSubstringEndIndex = lastIndex + 1
                }
            }

            // Log the substring.
            delegate.info(msgSubstring)

            // Recursively log the remainder.
            logLong(message.substring(msgSubstringEndIndex))
        } else {
            delegate.info(message)
        }// String to be logged is shorter than the max...
    }
}
