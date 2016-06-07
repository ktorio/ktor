package org.jetbrains.ktor.tests.application

import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.routing.*
import org.junit.*
import java.io.*
import java.net.*
import java.util.concurrent.*
import kotlin.concurrent.*

abstract class HostTestBase {
    protected val port = findFreePort()
    protected var server: ApplicationHost? = null

    @Before
    fun setUpBase() {
        println("Starting server on port $port")
    }

    @After
    fun tearDownBase() {
        server?.stop()
    }

    protected abstract fun createServer(port: Int, block: Routing.() -> Unit): ApplicationHost
    protected fun createAndStartServer(port: Int, block: Routing.() -> Unit): ApplicationHost {
        val server = createServer(port, block)
        startServer(server)

        return server
    }

    protected fun startServer(server: ApplicationHost) {
        this.server = server
        val l = CountDownLatch(1)
        thread {
            l.countDown()
            server.start()
        }
        l.await()

        do {
            Thread.sleep(50)
            try {
                Socket("localhost", port).close()
                return
            } catch (expected: IOException) {
            }
        } while (true)
    }

    protected fun findFreePort() = ServerSocket(0).use {  it.localPort }
    protected fun withUrl(path: String, block: HttpURLConnection.() -> Unit) {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = false

        connection.block()
    }

    protected fun PipelineContext<*>.failAndProceed(e: Throwable): Nothing {
        runBlock { fail(e) }
    }

    protected fun PipelineContext<*>.finishAllAndProceed(): Nothing {
        runBlock { finishAll() }
    }

}