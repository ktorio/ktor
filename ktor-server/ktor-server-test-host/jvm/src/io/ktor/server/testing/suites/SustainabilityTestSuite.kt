/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.application.*
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import org.junit.runners.model.*
import org.slf4j.*
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.concurrent.*
import kotlin.test.*

abstract class SustainabilityTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    @Test
    fun testLoggerOnError() {
        val message = "expected, ${Random().nextLong()}"
        val collected = LinkedBlockingQueue<Throwable>()

        val log = object : Logger by LoggerFactory.getLogger("ktor.test") {
            override fun error(message: String, exception: Throwable?) {
                if (exception != null) {
                    collected.add(exception)
                }
            }
        }

        createAndStartServer(log) {
            get("/") {
                throw ExpectedException(message)
            }
            get("/respondWrite") {
                call.respondTextWriter {
                    throw ExpectedException(message)
                }
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.InternalServerError.value, status.value)

            while (true) {
                val exception = collected.poll(timeout, TimeUnit.SECONDS)
                if (exception is ExpectedException) {
                    assertEquals(message, exception.message)
                    break
                }
            }
        }

        withUrl("/respondWrite") {
            assertEquals(HttpStatusCode.OK.value, status.value)
            while (true) {
                val exception = collected.poll(timeout, TimeUnit.SECONDS)
                if (exception is ExpectedException) {
                    assertEquals(message, exception.message)
                    break
                }
            }
        }
    }

    @Test
    fun testIgnorePostContent(): Unit = runBlocking {
        createAndStartServer {
            post("/") {
                call.respondText("OK")
            }
        }

        socket {
            val bodySize = 65536
            val repeatCount = 10
            val body = "X".repeat(bodySize).toByteArray()

            coroutineScope {
                launch(CoroutineName("writer") + testDispatcher) {
                    RequestResponseBuilder().apply {
                        requestLine(HttpMethod.Post, "/", HttpProtocolVersion.HTTP_1_1.toString())
                        headerLine(HttpHeaders.Host, "localhost:$port")
                        headerLine(HttpHeaders.Connection, "keep-alive")
                        headerLine(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                        headerLine(HttpHeaders.ContentLength, body.size.toString())
                        emptyLine()
                    }.build().use { request ->
                        repeat(repeatCount) {
                            getOutputStream().writePacket(request.copy())
                            getOutputStream().write(body)
                            getOutputStream().flush()
                        }
                    }
                }

                launch(CoroutineName("reader") + testDispatcher) {
                    use {
                        val channel =
                            getInputStream().toByteReadChannel(context = testDispatcher, pool = KtorDefaultPool)

                        repeat(repeatCount) { requestNumber ->
                            parseResponse(channel)?.use { response ->
                                assertEquals(200, response.status)
                                val contentLength = response.headers[HttpHeaders.ContentLength].toString().toLong()
                                channel.discardExact(contentLength)
                                response.release()
                            } ?: fail("No response found for request #$requestNumber")
                        }
                    }
                }
            }
        }
    }

    @Test
    @NoHttp2
    @Ignore
    open fun testChunkedWrongLength() {
        val data = ByteArray(16 * 1024, { it.toByte() })
        val doubleSize = (data.size * 2).toString()
        val halfSize = (data.size / 2).toString()

        createAndStartServer {
            get("/read-less") {
                assertFailsSuspend {
                    call.respond(object : OutgoingContent.ReadChannelContent() {
                        override val headers: Headers
                            get() = Headers.build {
                                append(HttpHeaders.ContentLength, doubleSize)
                            }

                        override fun readFrom(): ByteReadChannel = ByteReadChannel(data)
                    })
                }
            }
            get("/read-more") {
                assertFailsSuspend {
                    call.respond(object : OutgoingContent.ReadChannelContent() {
                        override val headers: Headers
                            get() = Headers.build {
                                append(HttpHeaders.ContentLength, halfSize)
                            }

                        override fun readFrom(): ByteReadChannel = ByteReadChannel(data)
                    })
                }
            }
            get("/write-less") {
                assertFailsSuspend {
                    call.respond(object : OutgoingContent.WriteChannelContent() {
                        override val headers: Headers
                            get() = Headers.build {
                                append(HttpHeaders.ContentLength, doubleSize)
                            }

                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeFully(data)
                            channel.close()
                        }
                    })
                }
            }
            get("/write-more") {
                assertFailsSuspend {
                    call.respond(object : OutgoingContent.WriteChannelContent() {
                        override val headers: Headers
                            get() = Headers.build {
                                append(HttpHeaders.ContentLength, halfSize)
                            }

                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeFully(data)
                            channel.close()
                        }
                    })
                }
            }
        }

        assertFails {
            withUrl("/read-more") {
                call.receive<String>()
            }
        }

        assertFails {
            withUrl("/write-more") {
                call.receive<String>()
            }
        }

        assertFails {
            withUrl("/read-less") {
                call.receive<String>()
            }
        }

        assertFails {
            withUrl("/write-less") {
                call.receive<String>()
            }
        }
    }

    @Test
    fun testApplicationScopeCancellation() {
        var job: Job? = null

        createAndStartServer {
            job = application.launch {
                delay(10000000L)
            }
        }

        server!!.stop(1, 10, TimeUnit.SECONDS)
        assertNotNull(job)
        assertTrue { job!!.isCancelled }
    }

    @Test
    fun testEmbeddedServerCancellation() {
        val parent = Job()

        createAndStartServer(parent = parent) {
            get("/") { call.respondText("OK") }
        }

        withUrl("/") {
            // ensure the server is running
            assertEquals("OK", call.receive<String>())
        }

        parent.cancel()

        runBlocking {
            withTimeout(5000L) {
                parent.join()
            }
        }

        assertFailsWith<IOException> {
            // ensure that the server is not running anymore
            withUrl("/") {
                call.receive<String>()
                fail("Shouldn't happen")
            }
        }
    }

    @Test
    fun testGetWithBody() {
        createAndStartServer {
            application.install(Compression)

            get("/") {
                call.respondText(call.receive())
            }
        }

        val text = "text body"

        withUrl("/", { body = text; }) {
            val actual = readText()
            assertEquals(text, actual)
        }
    }

    @Test
    fun testRepeatRequest() {
        createAndStartServer {
            get("/") {
                call.respond("OK ${call.request.queryParameters["i"]}")
            }
        }

        for (i in 1..100) {
            withUrl("/?i=$i") {
                assertEquals(200, status.value)
                assertEquals("OK $i", readText())
            }
        }
    }

    @Test
    fun testBlockingConcurrency() {
        val completed = AtomicInteger(0)
        createAndStartServer {
            get("/{index}") {
                val index = call.parameters["index"]!!.toInt()
                call.respondTextWriter {
                    //print("[$index] ")
                    try {
                        append("OK:$index\n")
                    } finally {
                        completed.incrementAndGet()
                    }
                }
            }
        }

        val count = 100
        val latch = CountDownLatch(count)
        val errors = CopyOnWriteArrayList<Throwable>()

        val random = Random()
        for (i in 1..latch.count) {
            thread {
                try {
                    withUrl("/$i") {
                        //setRequestProperty("Connection", "close")
                        content.toInputStream().reader().use { reader ->
                            val firstByte = reader.read()
                            if (firstByte == -1) {
                                //println("Premature end of response stream at iteration $i")
                                fail("Premature end of response stream at iteration $i")
                            } else {
                                assertEquals('O', firstByte.toChar())
                                Thread.sleep(random.nextInt(1000).toLong())
                                assertEquals("K:$i\n", reader.readText())
                            }
                        }
                    }
                } catch (t: Throwable) {
                    errors += t
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()

        if (errors.isNotEmpty()) {
            throw MultipleFailureException(errors)
        }

        var multiplier = 1
        if (enableHttp2) multiplier++
        if (enableSsl) multiplier++

        assertEquals(count * multiplier, completed.get())
    }

    @Test
    fun testBigFile() {
        val file = File("build/large-file.dat")
        val rnd = Random()

        if (!file.exists()) {
            file.bufferedWriter().use { out ->
                for (line in 1..9000000) {
                    for (col in 1..(30 + rnd.nextInt(40))) {
                        out.append('a' + rnd.nextInt(25))
                    }
                    out.append('\n')
                }
            }
        }

        val originalSha1WithSize = file.inputStream().use { it.crcWithSize() }

        createAndStartServer {
            get("/file") {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/file") {
            assertEquals(originalSha1WithSize, content.toInputStream().crcWithSize())
        }
    }

    @Test
    fun testBigFileHttpUrlConnection() {
        val file = File("build/large-file.dat")
        val rnd = Random()

        if (!file.exists()) {
            file.bufferedWriter().use { out ->
                for (line in 1..9000000) {
                    for (col in 1..(30 + rnd.nextInt(40))) {
                        out.append('a' + rnd.nextInt(25))
                    }
                    out.append('\n')
                }
            }
        }

        val originalSha1WithSize = file.inputStream().use { it.crcWithSize() }

        createAndStartServer {
            get("/file") {
                call.respond(LocalFileContent(file))
            }
        }

        val connection = URL("http://localhost:$port/file").openConnection(Proxy.NO_PROXY) as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000

        try {
            assertEquals(originalSha1WithSize, connection.inputStream.crcWithSize())
        } finally {
            connection.disconnect()
        }
    }

    @Test
    open fun testBlockingDeadlock() {
        createAndStartServer {
            get("/") {
                call.respondTextWriter(ContentType.Text.Plain.withCharset(Charsets.ISO_8859_1)) {
                    TimeUnit.SECONDS.sleep(1)
                    this.write("Deadlock ?")
                }
            }
        }

        val e = Executors.newCachedThreadPool()
        try {
            val q = LinkedBlockingQueue<String>()

            val conns = (0..callGroupSize * 10).map {
                e.submit(Callable<String> {
                    try {
                        URL("http://localhost:$port/").openConnection().inputStream.bufferedReader().readLine().apply {
                            //println("$number says $this")
                        } ?: "<empty>"
                    } catch (t: Throwable) {
                        "error: ${t.message}"
                    }.apply {
                        q.add(this)
                    }
                })
            }

            TimeUnit.SECONDS.sleep(5)
            var attempts = 7

            fun dump() {
//            val (valid, invalid) = conns.filter { it.isDone }.partition { it.get() == "Deadlock ?" }
//
//            println("Completed: ${valid.size} valid, ${invalid.size} invalid of ${conns.size} total [attempts $attempts]")
            }

            while (true) {
                dump()

                if (conns.all { it.isDone }) {
                    break
                } else if (q.poll(5, TimeUnit.SECONDS) == null) {
                    if (attempts <= 0) {
                        break
                    }
                    attempts--
                } else {
                    attempts = 7
                }
            }

            dump()

            /* use for debugging */
//        if (conns.any { !it.isDone }) {
//             TimeUnit.SECONDS.sleep(500)
//        }

            assertTrue { conns.all { it.isDone } }
        } finally {
            e.shutdownNow()
        }
    }

    @Test
    open fun testChunkedWithVSpace() {
        createAndStartServer {
            post("/") {
                try {
                    val post = call.receiveParameters()
                    call.respond("$post")
                    call.respond(HttpStatusCode.BadRequest, "")
                } catch (cause: Throwable) {
                    throw cause
                }
            }
        }

        val messages = listOf(
            "POST / HTTP/1.1\r\n",
            "Host:localhost\r\n",
            "Connection: close\r\n",
            "Content-Type: application/x-www-form-urlencoded\r\n",
            "Content-Length: 1\r\n",
            "Transfer-Encoding:\u000bchunked\r\n",
            "\r\n",
            "3\r\n",
            "a=1\r\n",
            "0\r\n",
            "\r\n",
        )

        socket {
            getOutputStream().writer().also { writer ->
                messages.forEach { writer.write(it) }
                writer.flush()
            }

            val result = getInputStream().reader().readLines().joinToString("\n")
            val expected = listOf(
                "HTTP/1.1 400",
                "HTTP/1.0 400"
            )

            assertTrue(expected.any { result.startsWith(it) },"Invalid response: $result")
        }
    }
}
