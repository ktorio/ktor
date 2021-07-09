/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING")

package io.ktor.server.testing.suites

import io.ktor.application.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.*
import org.junit.runners.model.*
import java.net.*
import java.nio.*
import java.util.*
import java.util.concurrent.*
import kotlin.concurrent.*
import kotlin.coroutines.*

@RunWith(StressSuiteRunner::class)
abstract class EngineStressSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

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
    private val endMarkerCrLfBytes = endMarkerCrLf.toByteArray()

    override val timeout: Long = TimeUnit.MILLISECONDS.toSeconds(timeMillis + gracefulMillis + shutdownMillis)

    @get:org.junit.Rule
    val timout1 = CoroutinesTimeout(200000L, true)

    @Test
    fun singleConnectionSingleThreadNoPipelining() {
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
                    val line = input.readLine() ?: throw AssertionError("Unexpected EOF")
                    if (endMarker in line) {
                        break
                    }
                }
            }
        }
    }

    @Test
    fun singleConnectionSingleThreadWithPipelining() {
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

        socket {
            val out = getOutputStream()
            val input = getInputStream().bufferedReader(Charsets.ISO_8859_1)
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

                    val line = input.readLine() ?: throw AssertionError("Unexpected EOF")
                    if (endMarker in line) {
                        sem.release()
                    }
                }
            } catch (t: Throwable) {
                readerFailure = t
                sender.interrupt()
                close()
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
    fun singleConnectionHighPressure() {
        createAndStartServer {
            get("/") {
                call.respondText(endMarkerCrLf)
            }
        }

        HighLoadHttpGenerator.doRun("/", "localhost", port, 1, 1, 10, true, gracefulMillis, timeMillis)

        sleepWhileServerIsRestoring()

        withUrl("/") {
            assertEquals(endMarkerCrLf, readText())
        }
    }

    @Test
    fun multipleConnectionsHighPressure() {
        createAndStartServer {
            get("/") {
                call.respondText(endMarkerCrLf)
            }
        }

        HighLoadHttpGenerator.doRun("/", "localhost", port, 1, 100, 10, true, gracefulMillis, timeMillis)

        sleepWhileServerIsRestoring()

        withUrl("/") {
            assertEquals(endMarkerCrLf, readText())
        }
    }

    @Test
    fun highLoadStressTest() {
        createAndStartServer {
            get("/") {
                call.respondText(endMarkerCrLf)
            }
        }

        HighLoadHttpGenerator.doRun("/", "localhost", port, 8, 50, 10, true, gracefulMillis, timeMillis)

        sleepWhileServerIsRestoring()

        withUrl("/") {
            assertEquals(endMarkerCrLf, readText())
        }
    }

    @Test
    fun testHttpUpgrade() {
        createAndStartServer {
            handle {
                call.respond(
                    object : OutgoingContent.ProtocolUpgrade() {
                        override suspend fun upgrade(
                            input: ByteReadChannel,
                            output: ByteWriteChannel,
                            engineContext: CoroutineContext,
                            userContext: CoroutineContext
                        ): Job {
                            return launch(engineContext) {
                                try {
                                    output.writeFully(endMarkerCrLfBytes)
                                    output.flush()
                                    delay(200)
                                } finally {
                                    output.close()
                                }
                            }
                        }
                    }
                )
            }
        }

        HighLoadHttpGenerator.doRun("localhost", port, 1, 100, 10, true, gracefulMillis, timeMillis) {
            requestLine(HttpMethod.Get, "/", "HTTP/1.1")
            headerLine(HttpHeaders.Host, "localhost")
            headerLine(HttpHeaders.Connection, "Upgrade")
            headerLine(HttpHeaders.Upgrade, "test")
            emptyLine()
        }

        socket {
            val r = RequestResponseBuilder().apply {
                requestLine(HttpMethod.Get, "/", "HTTP/1.1")
                headerLine(HttpHeaders.Host, "localhost")
                headerLine(HttpHeaders.Connection, "Upgrade")
                headerLine(HttpHeaders.Upgrade, "test")
                emptyLine()
            }.build()

            getOutputStream().apply {
                writePacket(r)
                flush()
            }

            getInputStream().bufferedReader().readLines()
        }
    }

    @Test
    fun testRespondWrite() {
        createAndStartServer {
            get("/") {
                call.respondTextWriter {
                    append(endMarker)
                    flush()
                    append("\r\n")
                }
            }
        }

        HighLoadHttpGenerator.doRun("/", "localhost", port, 8, 50, 10, false, gracefulMillis, timeMillis)

        withUrl("/") {
            assertEquals(endMarkerCrLf, readText())
        }
    }

    @Test
    fun test404() {
        createAndStartServer {
            get("/") {
                call.respondText("OK")
            }
        }

        HighLoadHttpGenerator.doRun("/404", "localhost", port, 8, 50, 10, false, gracefulMillis, timeMillis)

        withUrl("/") {
            assertEquals("OK", readText())
        }
    }

    @Test
    fun testLongResponse() {
        createAndStartServer {
            get("/ll") {
                call.respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            val bb: ByteBuffer = ByteBuffer.allocate(1024)
                            Random().nextBytes(bb.array())

                            for (i in 1..1024 * 1024) {
                                bb.clear()
                                while (bb.hasRemaining()) {
                                    channel.writeFully(bb)
                                }
                            }

                            channel.close()
                        }
                    }
                )
            }
            get("/") {
                call.respondText("OK")
            }
        }

        println("Starting...")
        HighLoadHttpGenerator.doRun("/ll", "localhost", port, 8, 50, 10, true, gracefulMillis, timeMillis)

        sleepWhileServerIsRestoring()

        withUrl("/") {
            assertEquals("OK", readText())
        }
    }

    private fun sleepWhileServerIsRestoring() {
        // after a high-pressure run it takes time for server to completely recover
        // so we sleep a little bit before doing a request
        Thread.sleep(10000)
    }
}
