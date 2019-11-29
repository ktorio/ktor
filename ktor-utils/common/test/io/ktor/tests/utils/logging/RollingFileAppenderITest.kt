/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import io.ktor.tests.utils.*
import io.ktor.util.date.*
import io.ktor.util.logging.*
import io.ktor.util.logging.rolling.*
import kotlinx.coroutines.*
import kotlin.jvm.*
import kotlin.test.*
import kotlin.time.*

@UseExperimental(ExperimentalTime::class)
class RollingFileAppenderITest {
    private val dispatcher = TestDelayDispatcher(0L)
    private val job = Job()
    private val fileSystem = ActualFileSystem()
    private val appender = object : AbstractRollingFileAppender(
        fileSystem,
        job + dispatcher,
        "build/tmp/test-log.log",
        FilePathPattern("build/tmp/test-log-%d{yyyyMMdd}-%i.log"),
        policy = RollingPolicy(
            maxFileSize = FileSize(10),
            maxTotalCount = 3,
            keepUntil = 10.days
        ),
        clock = { GMTDate(dispatcher.currentTimeMillis) }
    ) {

    }

    private val today: String
        get() = GMTDate(dispatcher.currentTimeMillis).format(StringPatternDateFormat("yyyyMMdd"))

    private val logger = Logger(Config.Empty.withAppender(appender))

    @Volatile
    private var failure: Throwable? = null

    @BeforeTest
    fun cleanupAndSetup() {
        fileSystem.list("build/tmp").forEach {
            if (it.endsWith(".log")) {
                fileSystem.delete(it)
            }
        }
        dispatcher.play()
        job.invokeOnCompletion { cause ->
            if (cause != null && cause.message != "Test completed") {
                failure = cause
                println(cause)
            }
        }
    }

    @AfterTest
    fun afterTest() {
        job.cancel("Test completed")
        dispatcher.play()
        failure?.let { throw it }
        assertEquals(0, dispatcher.queuedCount)
    }

    @Test
    fun smokeTest() {
        logger.info("test")

        assertEquals(5, fileSystem.size("build/tmp/test-log.log"))
    }

    @Test
    fun testRollingFileSize() {
        repeat(3) {
            logger.info("num$it")
        }
        dispatcher.currentTimeMillis += 11000

        // it the ideal world we would expect to see 5 and 10
        // but because of the delay a log file is not rolled immediately (that is by design)
        assertEquals(0, fileSystem.size("build/tmp/test-log.log"))
        assertEquals(15, fileSystem.size("build/tmp/test-log-$today-1.log"))
    }

    @Test
    fun testRollingTotalCount() {
        repeat(600) {
            logger.info("num$it")
            println("log")
            dispatcher.currentTimeMillis += 1000
        }
        dispatcher.currentTimeMillis += 11000

        assertEquals(3, fileSystem.list(appender.rolledFilePathPattern).count())
    }

    @Test
    fun testRollingDays() {
        val before = today
        logger.info("1")
        dispatcher.currentTimeMillis += 2.days.toLongMilliseconds()
        logger.info("2")
        dispatcher.currentTimeMillis += 2.days.toLongMilliseconds()

        assertEquals(2, fileSystem.size("build/tmp/test-log.log"))
        assertEquals(2, fileSystem.size("build/tmp/test-log-$before-1.log"))
    }

    @Test
    @Ignore
    fun testMaxAge() {
        val age = appender.policy.keepUntil.toLongMilliseconds()

        repeat(3) {
            logger.info(it.toString())
            dispatcher.currentTimeMillis += 1.days.toLongMilliseconds()
        }

        dispatcher.currentTimeMillis += age

        // 1 should be removed, 1 should be alive, 1 - current, not removed as well
        assertEquals(1, fileSystem.list(appender.rolledFilePathPattern).count())
    }
}
