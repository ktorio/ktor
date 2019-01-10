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


/***
 * Workaround Logger to log long messages that would otherwise be truncated in Android.
 */
val Logger.Companion.ANDROID: Logger
    get() = MaxLengthLogger()


class MaxLengthLogger(val MAX_INDEX : Int = 4000) : Logger {
    private val delegate = LoggerFactory.getLogger(HttpClient::class.java)!!
    override fun log(message: String) {
        logLong(message)
    }

    fun logLong(message: String) {

        val MIN_INDEX = 3000

        // String to be logged is longer than the max...
        if (message.length > MAX_INDEX) {
            var theSubstring = message.substring(0, MAX_INDEX)
            var theIndex = MAX_INDEX

            // Try to find a substring break at a line end.
            theIndex = theSubstring.lastIndexOf('\n')
            if (theIndex >= MIN_INDEX) {
                theSubstring = theSubstring.substring(0, theIndex)
            } else {
                theIndex = MAX_INDEX
            }

            // Log the substring.
            delegate.info(theSubstring)

            // Recursively log the remainder.
            logLong(message.substring(theIndex))
        } else {
            delegate.info(message)
        }// String to be logged is shorter than the max...
    }
}