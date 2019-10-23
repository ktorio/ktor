/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import io.ktor.util.logging.*
import io.ktor.util.logging.labels.*
import kotlin.test.*

class LoggerNameFilterTest {
    private val events = ArrayList<String>()
    private val appender = object : Appender {
        override fun append(record: LogRecord) {
            events.add(record.text)
        }

        override fun flush() {
        }
    }
    private val root = logger(appender) {
        name("root")
        level("*", Level.DEBUG)
        level("root.child", Level.INFO)
    }
    private val child = root.configure { name("child") }

    @Test
    fun testStarFilter() {
        root.trace("trace")
        root.debug("debug")
        root.info("info")

        assertEquals(listOf("debug", "info"), events)
    }

    @Test
    fun testSubFilter() {
        child.debug("debug")
        child.info("info")
        child.error("error")

        assertEquals(listOf("info", "error"), events)
    }

    @Test
    fun starAtStart() {
        val logger = child.configure {
            level("*.child", Level.WARNING)
        }
        val sub = logger.subLogger("sub")

        logger.info("should-not-pass")
        logger.error("error-should-pass")
        sub.info("info-should-pass")

        assertEquals(listOf("error-should-pass", "info-should-pass"), events)
    }

    @Test
    fun starAtEnd() {
        val logger = child.configure {
            level("root.*", Level.WARNING)
        }

        logger.info("info-should-not-pass")
        logger.warning("warning-should-pass")
        logger.error("error-should-pass")

        assertEquals(listOf("warning-should-pass", "error-should-pass"), events)
    }

    @Test
    fun starInTheMiddle() {
        val logger = child.configure {
            level("root.*child", Level.WARNING)
        }

        val sub = logger.subLogger("sub")

        logger.info("info-should-not-pass")
        logger.warning("warning-should-pass")
        sub.info("info-should-pass")

        assertEquals(listOf("warning-should-pass", "info-should-pass"), events)
    }

    @Test
    fun starsMultiple() {
        val logger = child.configure {
            name("sub")
            level("root.*ch*sub", Level.WARNING)
        }

        logger.info("info-should-not-pass")
        logger.warning("warning-should-pass")

        assertEquals(listOf("warning-should-pass"), events)
    }
}
