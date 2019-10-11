/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.util.date.*
import io.ktor.util.logging.*
import io.ktor.util.logging.labels.*
import kotlinx.coroutines.*
import kotlin.test.*

class LoggerTest {
    private val appender = TestAppender()
    private val logger = logger(appender)

    @Test
    fun smokeMessage() {
        logger.info("ok")

        assertEquals("[INFO] ok\n", appender.log())
    }

    @Test
    fun logLevels() {
        logger.trace("log-trace")
        logger.debug("log-debug")
        logger.info("log-info")
        logger.warning("log-warning")
        logger.error("log-error")

        assertEquals(
            "[TRACE] log-trace\n[DEBUG] log-debug\n[INFO] log-info\n[WARNING] log-warning\n[ERROR] log-error\n",
            appender.log()
        )
    }

    @Test
    fun configCustomLabel() {
        val child = logger.fork {
            label {
                append("custom-label")
            }
        }

        child.info("test-22")
        assertEquals("[INFO] [custom-label] test-22\n", appender.log())
    }

    @Test
    fun testEnrichAndLabel() {
        var state = 0
        val key = TestKey(-1)
        val key2 = TestKey(-2)

        val child = logger.fork {
            registerKey(key)
            registerKey(key2)

            enrich {
                this[key] = state
                this[key2] = state + 1
            }
            label { message ->
                append("state-")
                append(message[key].toString())
                append('-')
                append(message[key2].toString())
            }
        }

        state = 5
        child.info("msg1")
        state = 10
        child.info("msg2")

        assertEquals("[INFO] [state-5-6] msg1\n[INFO] [state-10-11] msg2\n", appender.log())
    }

    @Test
    fun testDiscard() {
        var state = 0

        val child = logger.fork {
            enrich {
                if (state == 2) discard()
            }
        }

        state = 1
        child.info("msg1")
        state = 2
        child.info("msg2")
        state = 1
        child.info("msg3")

        assertEquals("[INFO] msg1\n[INFO] msg3\n", appender.log())
    }

    @Test
    fun loggerNameTest() {
        val child = logger.subLogger("root")
        val subChild = child.subLogger("child")

        child.info("msg1")
        subChild.info("msg2")

        assertEquals("[INFO] [root] msg1\n[INFO] [root.child] msg2\n", appender.log())
    }

    @Test
    fun testDates() {
        var date = GMTDate(7)
        val child = logger.fork {
            logTime(clock = { date })
        }

        child.info("msg1")
        date = GMTDate(77)
        child.info("msg2")
        date = GMTDate(777)
        child.info("msg3")
        date = GMTDate(1777)
        child.info("msg4")

        date = GMTDate(1570614298261)
        child.info("msg5")

        assertEquals(
            "[INFO] [Thu, 01 Jan 1970 00:00:00.007] msg1\n" +
                "[INFO] [Thu, 01 Jan 1970 00:00:00.077] msg2\n" +
                "[INFO] [Thu, 01 Jan 1970 00:00:00.777] msg3\n" +
                "[INFO] [Thu, 01 Jan 1970 00:00:01.777] msg4\n" +
                "[INFO] [Wed, 09 Oct 2019 09:44:58.261] msg5\n", appender.log()
        )
    }

    @Test
    fun testReusableLogBuilder() {
        var instance: LogRecord? = null

        val logger = logger.fork {
            enrich {
                if (instance == null) {
                    instance = this
                }
                assertSame(instance, this)
            }
        }

        logger.info("msg1")
        logger.info("msg2")
        logger.info("msg3")
        assertNotNull(instance)
    }

    @Test
    fun loggerCreationLambda() {
        val logger = logger {
            addAppender(appender)
            name("custom-config")
        }

        logger.info("msg1")

        assertEquals("[custom-config] msg1\n", appender.log())
    }

    @Test
    fun loggerCreationAppender() {
        val logger = logger(appender)

        logger.info("msg2")

        assertEquals("[INFO] msg2\n", appender.log())
    }

    @Test
    fun loggerCreationForClass() {
        val logger = logger.forClass<TestAppender>()

        logger.info("msg1")

        assertEquals("[INFO] [TestAppender] msg1\n", appender.log())
    }

    @Test
    fun loggerCreationForThisClass() {
        val logger = loggerForClass(logger)

        logger.info("msg1")

        assertEquals("[INFO] [LoggerTest] msg1\n", appender.log())
    }

    @Test
    fun loggerCreationForThisClassWithConfig() {
        val logger = loggerForClass(logger.config)

        logger.info("msg7")

        assertEquals("[INFO] [LoggerTest] msg7\n", appender.log())
    }

    @Test
    fun loggerCreationForThisClass2() {
        class Test {
            val logger = loggerForClass(this@LoggerTest.logger)
            fun f() {
                logger.info("msg3")
            }
        }

        Test().f()

        assertEquals("[INFO] [Test] msg3\n", appender.log())
    }

    private class TestAppender : Appender {
        private val writer = StringBuilder(8192)
        private val delegate = TextAppender(Appendable::formatLogRecordDefault) {
            writer.append(it); writer.append("\n")
        }

        override fun append(record: LogRecord) {
            delegate.append(record)
        }

        override fun flush() {
            delegate.flush()
        }

        fun log(): String = writer.toString()
    }

    private class TestKey<T>(initial: T) : LogAttributeKey<T>("test-key", initial)
}
