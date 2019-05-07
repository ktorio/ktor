/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.logging

import kotlin.test.*

class AndroidLoggerTest {
    private val messages = ArrayList<String>()
    private val destinationLogger = object : Logger {
        override fun log(message: String) {
            messages.add(message)
        }
    }

    @Test
    fun usage() {
        Logger.ANDROID
    }

    @Test
    fun shortMessage() {
        MessageLengthLimitingLogger(delegate = destinationLogger).log("short message")
        assertEquals(listOf("short message"), messages)
    }

    @Test
    fun longMessage() {
        val logger = MessageLengthLimitingLogger(maxLength = 10, minLength = 5, delegate = destinationLogger)
        logger.log("line1\nline2")
        assertEquals(listOf("line1", "line2"), messages)
    }

    @Test
    fun longMessageWithMinLength() {
        val logger = MessageLengthLimitingLogger(maxLength = 10, minLength = 6, delegate = destinationLogger)
        logger.log("line1\nline2")
        assertEquals(listOf("line1\nline", "2"), messages)
    }

    @Test
    fun longMessageMultiline() {
        val logger = MessageLengthLimitingLogger(maxLength = 10, minLength = 5, delegate = destinationLogger)
        logger.log("line1\nline2\nline3")
        assertEquals(listOf("line1", "line2", "line3"), messages)
    }

    @Test
    fun longMessageWithNoLineBreaks() {
        val logger = MessageLengthLimitingLogger(maxLength = 10, minLength = 5, delegate = destinationLogger)
        logger.log("0123456789abcdef")
        assertEquals(listOf("0123456789", "abcdef"), messages)
    }
}
