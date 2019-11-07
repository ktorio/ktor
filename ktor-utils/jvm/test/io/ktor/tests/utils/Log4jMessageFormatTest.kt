/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.util.date.*
import io.ktor.util.logging.*
import io.ktor.util.logging.compat.*
import io.ktor.util.logging.labels.*
import java.text.*
import kotlin.test.*

class Log4jMessageFormatTest {
    private val date = GMTDate(SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2019-11-01 11:22:33").time)
    private val results = ArrayList<String>()

    @Test
    fun smokeTest() {
        val logger = loggerForPattern("%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n")
        logger.warning("test message")
        logger.info("test message 2")
        logger.addName("logger-name").info("message 3")

        assertEquals(3, results.size)
        assertEquals("11:22:33.000 [Test worker] WARNI  - test message", results[0])
        assertEquals("11:22:33.000 [Test worker] INFO   - test message 2", results[1])
        assertEquals("11:22:33.000 [Test worker] INFO  logger-name - message 3", results[2])
    }

    @Test
    fun testDateDefault() {
        val logger = loggerForPattern("%d")
        logger.info("")

        assertEquals(1, results.size)
        assertEquals("2019-11-01 11:22:33,000", results[0])
    }

    @Test
    fun testDateWithFormat() {
        val logger = loggerForPattern("%d{HH}")
        logger.info("")

        assertEquals(1, results.size)
        assertEquals("11", results[0])
    }

    @Test
    fun testDateDefaultLong() {
        val logger = loggerForPattern("%date")
        logger.info("")

        assertEquals(1, results.size)
        assertEquals("2019-11-01 11:22:33,000", results[0])
    }

    @Test
    fun testDateWithFormatLong() {
        val logger = loggerForPattern("%date{HH}")
        logger.info("")

        assertEquals(1, results.size)
        assertEquals("11", results[0])
    }

    @Test
    fun testThreadName() {
        val expectedThreadName = Thread.currentThread().name

        loggerForPattern("%t").info("")
        loggerForPattern("%tn").info("")
        loggerForPattern("%thread").info("")
        loggerForPattern("%threadName").info("")

        assertEquals(
        listOf(expectedThreadName, expectedThreadName, expectedThreadName, expectedThreadName), results)
    }

    @Test
    fun testLogLevel() {
        val logger = loggerForPattern("%level")
        logger.info("")
        logger.warning("")

        assertEquals(2, results.size)
        assertEquals("INFO", results[0])
        assertEquals("WARNING", results[1])
    }

    @Test
    fun testLogLevelTruncated() {
        val logger = loggerForPattern("%-5level")
        logger.info("")
        logger.warning("")

        assertEquals(2, results.size)
        assertEquals("INFO ", results[0])
        assertEquals("WARNI", results[1])
    }

    @Test
    fun testLogLevelConfigured() {
        loggerForPattern("%level{length=1}").apply {
            info("")
            warning("")
        }
        loggerForPattern("%level{length=1,lowerCase=true}").apply {
            info("")
            warning("")
        }
        loggerForPattern("%level{INFO=z}").apply {
            info("")
            warning("")
        }
        loggerForPattern("%p{}").apply {
            info("")
        }

        assertEquals(listOf("I", "W", "i", "w", "z", "WARNING", "INFO"), results)
    }

    @Test
    fun testLoggerName() {
        val logger = loggerForPattern("%logger")
        logger.info("")
        logger.addName("logger").info("")
        logger.addName("logger").addName("name").info("")

        assertEquals(listOf("logger", "logger.name"), results)
    }

    @Test
    fun testLoggerNameShort() {
        val logger = loggerForPattern("%c")
        logger.info("")
        logger.addName("logger").info("")
        logger.addName("logger").addName("name").info("")
        logger.addName("logger").addName("name").addName("extra").info("")

        assertEquals(listOf("logger", "logger.name", "logger.name.extra"), results)
    }

    @Test
    fun testLoggerNameTruncatedPositive() {
        val logger = loggerForPattern("%logger{2}")
        logger.info("")
        logger.addName("logger").info("")
        logger.addName("logger").addName("name").info("")
        logger.addName("logger").addName("name").addName("extra").info("")

        assertEquals(listOf("logger", "logger.name", "name.extra"), results)
    }

    @Test
    fun testLoggerNameTruncatedNegative() {
        val logger = loggerForPattern("%logger{-2}")
        logger.info("")
        logger.addName("logger").info("")
        logger.addName("logger.name").info("")
        logger.addName("logger.name.extra").info("")
        logger.addName("logger.name.extra.more").info("")

        assertEquals(listOf("logger", "logger.name", "extra", "extra.more"), results)
    }

    @Test
    fun testLoggerNameTruncatedPatternSimple() {
        val logger = loggerForPattern("%logger{1.}")
        logger.info("")
        logger.addName("logger").info("")
        logger.addName("logger.name").info("")
        logger.addName("logger.name.extra").info("")
        logger.addName("logger.name.extra.more").info("")

        assertEquals(listOf("logger", "l.name", "l.n.extra", "l.n.e.more"), results)
    }

    @Test
    fun testLoggerNameTruncatedPatternSimple2() {
        val logger = loggerForPattern("%logger{2.}")
        logger.info("")
        logger.addName("logger").info("")
        logger.addName("logger.name").info("")
        logger.addName("logger.name.extra").info("")
        logger.addName("logger.name.extra.more").info("")
        logger.addName("a.b.c").info("")

        assertEquals(listOf("logger", "lo.name", "lo.na.extra", "lo.na.ex.more", "a.b.c"), results)
    }

    @Test
    fun testLoggerNameTruncatedPatternComplex() {
        loggerForPattern("%logger{.}").addName("a.b.c").info("")
        loggerForPattern("%logger{.}").addName("x").info("")
        loggerForPattern("%logger{1.2}").addName("123.123.123").info("")
        loggerForPattern("%logger{1.1.~.~}").addName("123.123.123").info("")
        loggerForPattern("%logger{1.1.~.~}").addName("abc.def.ghi.name").info("")
        loggerForPattern("%logger{1.1.~.~}").addName("abc.def.ghi.jkl.name").info("")
        loggerForPattern("%logger{1.1.~.~}").addName("abc.def.ghi.jkl.more.name").info("")

        assertEquals(listOf("..c", "x", "1.12.123", "1.1.123", "a.d.~.name", "a.d.~.~.name", "a.d.~.~.~.name"), results)
    }

    @Test
    fun testMessageAndLineFeed() {
        val logger = loggerForPattern("%level%n%msg")
        logger.info("msg1")
        logger.warning("msg2")

        assertEquals(listOf("INFO\nmsg1", "WARNING\nmsg2"), results)
    }

    @Test
    fun testClassNameFull() {
        loggerForPattern("%C").info("")

        assertEquals(listOf(Log4jMessageFormatTest::class.java.name), results)
    }

    @Test
    fun testClassNamePrecision() {
        loggerForPattern("%C{1}").info("")

        assertEquals(listOf(Log4jMessageFormatTest::class.java.simpleName), results)
    }

    @Test
    fun testFileName() {
        loggerForPattern("%F").info("")

        assertEquals(listOf(Log4jMessageFormatTest::class.java.simpleName + ".kt"), results)
    }

    @Test
    fun testLineNumber() {
        loggerForPattern("%L").info("")

        assertTrue { results.single().toInt() in 100 .. 1000 }
    }

    @Test
    fun testLocations() {
        loggerForPattern("%l").info("")

        val text = results.single()

        assertTrue { Log4jMessageFormatTest::class.java.simpleName in text }
        assertTrue { "testLocations" in text }
    }

    @Test
    fun testMethod() {
        loggerForPattern("%M").info("")

        assertEquals(listOf("testMethod"), results)
    }

    @Test
    fun testNanoseconds() {
        loggerForPattern("%N").info("")
        val nanoseconds = date.timestamp * 1000_000

        val actualNanosLogged = results.single().toLong()
        assertTrue { actualNanosLogged  == nanoseconds }
    }

    private fun loggerForPattern(pattern: String): Logger {
        val builder = LoggingConfigBuilder()
        val formatter = parseLog4jFormat(
            pattern, builder
        ) { date }

        builder.addAppender(TextAppender(formatter, { results.add(it.toString()) }))
        val config = builder.build()

        return logger(config)
    }
}
