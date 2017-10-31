package io.ktor.client.tests.utils

import ch.qos.logback.classic.*
import ch.qos.logback.classic.Logger
import io.ktor.client.*
import io.ktor.client.backend.*
import io.ktor.server.engine.*
import org.junit.*
import org.slf4j.*
import java.net.*
import java.util.concurrent.*

abstract class TestWithKtor(private val backendFactory: HttpClientBackendFactory) {
    abstract val server: ApplicationEngine
    protected val port: Int = ServerSocket(0).use { it.localPort }

    init {
        (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as? Logger)?.level = Level.ERROR
    }

    fun createClient(block: ClientConfig.() -> Unit = {}) = HttpClient(backendFactory, block)

    @Before
    fun startServer() {
        server.start()
    }

    @After
    fun stopServer() {
        server.stop(0, 0, TimeUnit.SECONDS)
    }
}
