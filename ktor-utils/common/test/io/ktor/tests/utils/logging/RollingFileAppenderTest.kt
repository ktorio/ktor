/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import io.ktor.util.date.*
import io.ktor.util.logging.*
import io.ktor.util.logging.labels.*
import io.ktor.util.logging.rolling.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.*

@UseExperimental(ExperimentalTime::class)
class RollingFileAppenderTest {
    private val job = Job()
    private val fileSystem = TestFileSystem()
    private var time: Long = 0

    @AfterTest
    fun cleanup() {
        job.cancel("Test completed")
    }

    @Test
    fun singleFile(): Unit = test("file-%i") { logger ->
        logger.info("test")
        expectedList("file.log")
        fileSystem.assertFileContent("file.log", "[INFO] [1970.1] test\n")

        logger.info("one more line")
        expectedList("file.log")
        fileSystem.assertFileContent(
            "file.log",
            "[INFO] [1970.1] test\n[INFO] [1970.1] one more line\n"
        )
    }

    private fun test(pattern: String, block: (Logger) -> Unit) {
        val appender = Appender(pattern)
        val config = LoggingConfigBuilder().apply {
            addAppender(appender)
            label { record ->
                append(record.level.name)
            }
            logTime(clock = {
                GMTDate(time)
            }, dateFormat = {
                append(it.year.toString())
                append(".")
                append(it.dayOfMonth.toString())
            })
        }.build()
        val logger = logger(config)
        block(logger)
    }

    private fun expectedList(vararg expectedPaths: String) {
        val actualPaths = fileSystem.allFiles.filterIsInstance<TestFileSystem.Entry.File>().map { it.path }
        assertEquals(expectedPaths.toList().sorted(), actualPaths.sorted())
    }

    private inner class Appender(pattern: String) : AbstractRollingFileAppender(
        fileSystem, job, "file.log",
        FilePathPattern(pattern)
    )
}
