package io.ktor.client.tests.utils

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import io.ktor.host.ApplicationHost
import org.junit.After
import org.junit.Before
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit


abstract class TestWithKtor {
    abstract val server: ApplicationHost

    init {
        (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? Logger)?.level = Level.ERROR
    }

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0, 0, TimeUnit.SECONDS)
    }
}
