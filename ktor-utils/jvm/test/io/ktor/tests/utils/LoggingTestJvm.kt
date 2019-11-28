/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.util.logging.*
import io.ktor.util.logging.labels.*
import kotlinx.coroutines.*
import java.lang.StringBuilder
import java.util.concurrent.*
import kotlin.test.*

@Suppress("RemoveExplicitTypeArguments")
class LoggingTestJvm {
    @Test
    fun testAsyncAppender(): Unit = runBlocking<Unit> {
        val job = Job(coroutineContext[Job]!!)
        val lock = CountDownLatch(1)
        val out = StringBuilder()
        val textAppender = TextAppender {
            lock.await()
            out.appendln(it)
        }
        val asyncAppender = AsyncAppender(textAppender, 3, job)
        val logger = logger(asyncAppender)

        repeat(11) {
            logger.info("msg$it")
        }

        lock.countDown()
        asyncAppender.close()
        job.complete()
        job.join()

        assertEquals("[INFO] msg0\n[INFO] msg1\n[INFO] msg2\n", out.toString())
    }

    @Test
    fun testBufferedAppender(): Unit = runBlocking {
        val job = Job(coroutineContext[Job]!!)
        var counter = 0
        val out = StringBuilder()
        val textAppender = TextAppender {
            counter++
            out.appendln(it)
        }
        val asyncAppender = AsyncAppender(textAppender, parent = job)
        val logger = logger(asyncAppender)

        repeat(3) {
            logger.info("msg$it")
        }

        asyncAppender.close()
        job.complete()
        job.join()

        assertEquals("[INFO] msg0\n[INFO] msg1\n[INFO] msg2\n", out.toString())
        assertEquals(1, counter)
    }

    @Test
    fun testCoroutinesLogging(): Unit = runBlocking<Unit> {
        val log = StringBuffer()
        val appender = TextAppender {
            log.appendln(it)
        }
        val logger = logger {
            addAppender(appender)
            label {
                append()
            }
        }

        logger.info("to")
    }

    @Test
    fun testLocations(): Unit = runBlocking<Unit> {
        val log = StringBuffer()
        val appender = TextAppender {
            log.appendln(it)
        }
        val logger = logger {
            addAppender(appender)
            locations()
        }

        logger.info("msg") // should be at line 91!

        assertEquals(
            "[io.ktor.tests.utils.LoggingTestJvm\$testLocations\$1.invokeSuspend (LoggingTestJvm.kt:91)] msg\n",
            log.toString()
        )
    }
}
