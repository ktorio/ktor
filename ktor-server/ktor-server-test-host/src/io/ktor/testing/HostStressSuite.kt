package io.ktor.testing

import io.ktor.client.*
import io.ktor.host.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.tests.http.*
import org.junit.*
import org.junit.runners.model.*
import java.net.*
import java.util.concurrent.*
import kotlin.concurrent.*
import kotlin.test.*

abstract class HostStressSuite<THost : ApplicationHost>(hostFactory: ApplicationHostFactory<THost>) : HostTestBase<THost>(hostFactory) {
    init {
        enableHttp2 = false
        enableSsl = false
    }

//    private val timeMillis: Long = TimeUnit.SECONDS.toMillis(10L)
    private val timeMillis: Long = TimeUnit.MINUTES.toMillis(2L)
    private val gracefulMillis: Long = TimeUnit.SECONDS.toMillis(20L)
    private val shutdownMillis: Long = TimeUnit.SECONDS.toMillis(40L)

    private val endMarker = "<< END >>"
    private val endMarkerCrLf = endMarker + "\r\n"

    @get:Rule
    override val timeout = PublishedTimeout(TimeUnit.MILLISECONDS.toSeconds(timeMillis + gracefulMillis + shutdownMillis))

    @Test
    fun `single connection single thread no pipelining`() {
        createAndStartServer {
            get("/") {
                call.respondText(endMarkerCrLf)
            }
        }

        val request = buildString {
            append("GET / HTTP/1.1\r\n")
            append("Host: localhost:$port\r\n")
            append("Connection: keep-alive\r\n")
            append("\r\n")
        }.toByteArray()

        Socket("localhost", port).use { socket ->
            socket.tcpNoDelay = true

            val out = socket.getOutputStream()
            val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val start = System.currentTimeMillis()

            while (true) {
                val now = System.currentTimeMillis()
                if (now - start >= timeMillis) break

                out.write(request)
                out.flush()

                while (true) {
                    val line = input.readLine() ?: fail("Unexpected EOF")
                    if (endMarker in line) {
                        break
                    }
                }
            }
        }
    }

    @Test
    fun `single connection single thread with pipelininig`() {
        createAndStartServer {
            get("/") {
                call.respondText(endMarkerCrLf)
            }
        }

        val request = buildString {
            append("GET / HTTP/1.1\r\n")
            append("Host: localhost:$port\r\n")
            append("Connection: keep-alive\r\n")
            append("\r\n")
        }.toByteArray()

        Socket("localhost", port).use { socket ->
            socket.tcpNoDelay = true

            val out = socket.getOutputStream()
            val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val start = System.currentTimeMillis()
            val sem = Semaphore(10)
            var writerFailure: Throwable? = null

            val sender = thread(name = "http-sender") {
                try {
                    while (true) {
                        val now = System.currentTimeMillis()
                        if (now - start >= timeMillis) break

                        if (!sem.tryAcquire(1000L, TimeUnit.MILLISECONDS)) continue

                        out.write(request)
                        out.flush()
                    }
                } catch (expected: InterruptedException) {
                } catch (t: Throwable) {
                    writerFailure = t
                }
            }

            var readerFailure: Throwable? = null

            try {
                while (true) {
                    val now = System.currentTimeMillis()
                    if (now - start >= timeMillis) break

                    val line = input.readLine() ?: fail("Unexpected EOF")
                    if (endMarker in line) {
                        sem.release()
                    }
                }
            } catch (t: Throwable) {
                readerFailure = t
                sender.interrupt()
                socket.close()
            }

            sender.join()
            if (readerFailure != null && writerFailure != null) {
                throw MultipleFailureException(listOf(readerFailure, writerFailure))
            }
            readerFailure?.let { throw it }
            writerFailure?.let { throw it }
        }
    }

    @Test
    fun `single connection high pressure`() {
        createAndStartServer {
            get("/") {
                call.respondText(endMarkerCrLf)
            }
        }

        val generator = HighLoadHttpGenerator("/", "localhost", port, 1, 10, true)
        val t = thread {
            println("Running...")
            generator.mainLoop()
        }

        try {
            Thread.sleep(timeMillis)
            println("Shutting down...")
            generator.shutdown()
            t.join(gracefulMillis)
        } finally {
            println("Termination...")
            generator.stop()
            t.interrupt()
            t.join()
            println("Terminated.")
        }

        withUrl("/") {
            assertEquals(endMarkerCrLf, readText())
        }
    }

    @Test
    fun `multiple connections high pressure`() {
        createAndStartServer {
            get("/") {
                call.respondText(endMarkerCrLf)
            }
        }

        val generator = HighLoadHttpGenerator("/", "localhost", port, 100, 10, true)
        val t = thread {
            println("Running...")
            generator.mainLoop()
        }

        try {
            Thread.sleep(timeMillis)
            println("Shutting down...")
            generator.shutdown()
            t.join(gracefulMillis)
        } finally {
            println("Termination...")
            generator.stop()
            t.interrupt()
            t.join()
            println("Terminated.")
        }

        withUrl("/") {
            assertEquals(endMarkerCrLf, readText())
        }
    }

    @Test
    fun `high load stress test`() {
        createAndStartServer {
            get("/") {
                call.respondText(endMarkerCrLf)
            }
        }

        val numberOfThreads = 8
        val connectionsPerThread = 50
        val queueSize = 10

        val generator = HighLoadHttpGenerator("/", "localhost", port, connectionsPerThread, queueSize, true)
        val threads = (1..numberOfThreads).map {
            thread {
                println("Running...")
                generator.mainLoop()
            }
        }
        val joiner = thread(start = false) {
            threads.forEach {
                it.join(gracefulMillis)
            }
        }

        try {
            Thread.sleep(timeMillis)
            println("Shutting down...")
            generator.shutdown()
            joiner.start()
            joiner.join(gracefulMillis)
        } finally {
            println("Termination...")
            generator.stop()
            threads.forEach { it.interrupt() }
            joiner.join()
            println("Terminated.")
        }

        withUrl("/") {
            assertEquals(endMarkerCrLf, readText())
        }
    }
}