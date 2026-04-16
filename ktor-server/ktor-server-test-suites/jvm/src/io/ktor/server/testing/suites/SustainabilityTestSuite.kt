/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.server.testing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.DebugProbes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.AbstractLogger
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

abstract class SustainabilityTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    @Test
    fun testLoggerOnError() = runTest {
        val message = "expected, ${Random().nextLong()}"
        val collected = LinkedBlockingQueue<Throwable>()

        val log = object : Logger by LoggerFactory.getLogger("io.ktor.test") {
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
                val exception = collected.poll(timeout.inWholeSeconds, TimeUnit.SECONDS)
                if (exception is ExpectedException) {
                    assertEquals(message, exception.message)
                    break
                }
            }
        }

        withUrl("/respondWrite") {
            assertEquals(HttpStatusCode.OK.value, status.value)
            while (true) {
                val exception = collected.poll(timeout.inWholeSeconds, TimeUnit.SECONDS)
                if (exception is ExpectedException) {
                    assertEquals(message, exception.message)
                    break
                }
            }
        }
    }

    @Test
    fun testIgnorePostContent(): Unit = runTest {
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
                            getOutputStream().writePacket(request.peek())
                            getOutputStream().write(body)
                            getOutputStream().flush()
                        }
                    }
                }

                launch(CoroutineName("reader") + testDispatcher) {
                    use {
                        val channel = getInputStream().toByteReadChannel(
                            context = testDispatcher,
                            pool = KtorDefaultPool
                        )

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
    @Http1Only
    @Ignore
    open fun testChunkedWrongLength() = runTest {
        val data = ByteArray(16 * 1024) { it.toByte() }
        val doubleSize = (data.size * 2).toString()
        val halfSize = (data.size / 2).toString()

        createAndStartServer {
            get("/read-less") {
                assertFailsSuspend {
                    call.respond(
                        object : OutgoingContent.ReadChannelContent() {
                            override val headers: Headers
                                get() = Headers.build {
                                    append(HttpHeaders.ContentLength, doubleSize)
                                }

                            override fun readFrom(): ByteReadChannel = ByteReadChannel(data)
                        }
                    )
                }
            }
            get("/read-more") {
                assertFailsSuspend {
                    call.respond(
                        object : OutgoingContent.ReadChannelContent() {
                            override val headers: Headers
                                get() = Headers.build {
                                    append(HttpHeaders.ContentLength, halfSize)
                                }

                            override fun readFrom(): ByteReadChannel = ByteReadChannel(data)
                        }
                    )
                }
            }
            get("/write-less") {
                assertFailsSuspend {
                    call.respond(
                        object : OutgoingContent.WriteChannelContent() {
                            override val headers: Headers
                                get() = Headers.build {
                                    append(HttpHeaders.ContentLength, doubleSize)
                                }

                            override suspend fun writeTo(channel: ByteWriteChannel) {
                                channel.writeFully(data)
                                channel.flushAndClose()
                            }
                        }
                    )
                }
            }
            get("/write-more") {
                assertFailsSuspend {
                    call.respond(
                        object : OutgoingContent.WriteChannelContent() {
                            override val headers: Headers
                                get() = Headers.build {
                                    append(HttpHeaders.ContentLength, halfSize)
                                }

                            override suspend fun writeTo(channel: ByteWriteChannel) {
                                channel.writeFully(data)
                                channel.flushAndClose()
                            }
                        }
                    )
                }
            }
        }

        assertFails {
            withUrl("/read-more") {
                call.body<String>()
            }
        }

        assertFails {
            withUrl("/write-more") {
                call.body<String>()
            }
        }

        assertFails {
            withUrl("/read-less") {
                call.body<String>()
            }
        }

        assertFails {
            withUrl("/write-less") {
                call.body<String>()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testEmbeddedServerCancellation() = runTest {
        val parent = Job()

        createAndStartServer(parent = parent) {
            get("/") { call.respondText("OK") }
        }

        withUrl("/") {
            // ensure the server is running
            assertEquals("OK", call.body<String>())
        }

        parent.cancel()

        val timeMillis = 15000L
        try {
            withTimeout(timeMillis) {
                parent.join()
            }
        } catch (_: TimeoutCancellationException) {
            DebugProbes.printJob(parent)
            fail("Server did not shut down within timeout (${timeMillis / 1000}s)!")
        }

        assertFailsWith<IOException> {
            // ensure that the server is not running anymore
            withUrl("/") {
                call.body<String>()
                fail("Shouldn't happen")
            }
        }
    }

    @Test
    fun testGetWithBody() = runTest {
        createAndStartServer {
            install(Compression)

            get("/") {
                call.respondText(call.receive())
            }
        }

        val text = "text body"

        withUrl("/", { setBody(text) }) {
            val actual = bodyAsText()
            assertEquals(text, actual)
        }
    }

    @Test
    fun testRepeatRequest() = runTest {
        createAndStartServer {
            get("/") {
                call.respond("OK ${call.request.queryParameters["i"]}")
            }
        }

        for (i in 1..100) {
            withUrl("/?i=$i") {
                assertEquals(200, status.value)
                assertEquals("OK $i", bodyAsText())
            }
        }
    }

    @OptIn(InternalAPI::class, ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    @Test
    open fun testBlockingConcurrency() = runTest {
        val completed = AtomicInteger(0)
        createAndStartServer {
            get("/{index}") {
                val index = call.parameters["index"]!!.toInt()
                call.respondTextWriter {
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
        for (i in 1..count) {
            val dispatcher = newSingleThreadContext("thread-$i")
            launch(dispatcher) {
                try {
                    withUrl("/$i") {
                        rawContent.toInputStream().reader().use { reader ->
                            val firstByte = reader.read()
                            if (firstByte == -1) {
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
            }.invokeOnCompletion {
                dispatcher.close()
            }
        }

        latch.await()

        if (errors.isNotEmpty()) {
            throw RuntimeException(
                "Exceptions thrown: ${errors.joinToString { it::class.simpleName ?: "<no name>" }}",
                errors.first()
            )
        }
        var multiplier = 1
        if (enableHttp2) multiplier++
        if (enableSsl) multiplier++

        assertEquals(count * multiplier, completed.get())
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testBigFile() = runTest {
        val file = File("build/large-file.dat")
        val rnd = Random()

        if (!file.exists()) {
            file.bufferedWriter().use { out ->
                repeat(9000000) {
                    repeat(30 + rnd.nextInt(40)) {
                        out.append('a' + rnd.nextInt(25))
                    }
                    out.append('\n')
                }
            }
        }

        val (fileChecksum, fileCount) = file.inputStream().use { it.crcWithSize() }

        createAndStartServer {
            get("/file") {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/file") {
            val (actualChecksum, actualCount) = body<InputStream>().crcWithSize()
            assertEquals(fileCount, actualCount, "Response size differs from file")
            assertEquals(fileChecksum, actualChecksum, "Response checksum differs from file")
        }
    }

    @Test
    fun testBigFileHttpUrlConnection() = runTest {
        val file = File("build/large-file.dat")
        val rnd = Random()

        if (!file.exists()) {
            file.bufferedWriter().use { out ->
                repeat(9000000) {
                    repeat(30 + rnd.nextInt(40)) {
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
    open fun testBlockingDeadlock() = runTest {
        createAndStartServer {
            get("/") {
                call.respondTextWriter(ContentType.Text.Plain.withCharset(Charsets.ISO_8859_1)) {
                    TimeUnit.SECONDS.sleep(1)
                    write("Deadlock ?")
                }
            }
        }

        val results = (0 until callGroupSize * 10).map {
            async {
                runCatching {
                    val response = client.get("http://127.0.0.1:$port/") {}
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals("Deadlock ?", response.bodyAsText())
                }
            }
        }.awaitAll()

        val failedCnt = results.count { it.isFailure }
        assertEquals(0, failedCnt)
    }

    @Test
    open fun testChunkedWithVSpace() = runTest {
        createAndStartServer {
            post("/") {
                call.receiveParameters()
                call.respond(HttpStatusCode.BadRequest, "")
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
            "\r\n"
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

            assert(expected.any { result.startsWith(it) }) {
                "Invalid response: $result"
            }
        }
    }

    @Test
    fun testChunkedIsNotFinal() = runTest {
        createAndStartServer {
            get("/") {
                call.respondText("Hello, world!", ContentType.Text.Html)
            }
            post("/") {
                call.receiveParameters()
                fail("We should NOT receive any content")
            }
        }

        val messages = listOf(
            "POST / HTTP/1.1\r\n",
            "Host:localhost\r\n",
            "Connection: close\r\n",
            "Content-Length: 1\r\n",
            "Content-Type: application/x-www-form-urlencoded\r\n",
            "Transfer-Encoding: chunked, smuggle\r\n",
            "\r\n",
            "3\r\n",
            "a=1\r\n",
            "0\r\n",
            "\r\n"
        )

        socket {
            getOutputStream().writer().also { writer ->
                messages.forEach { writer.write(it) }
                writer.flush()
            }

            val result = getInputStream().reader().readLines().joinToString("\n")
            val expected = listOf(
                "HTTP/1.1 501",
                "HTTP/1.1 400",
                "HTTP/1.0 400"
            )

            assert(expected.any { result.startsWith(it) }) {
                "Invalid response: $result"
            }
        }
    }

    @Ignore("Flaky. To be investigated in KTOR-7811")
    @Test
    @Http1Only
    fun testHeaderIsTooLong() = runTest {
        createAndStartServer {
            get("/") {
                call.respondText("Hello, world!", ContentType.Text.Plain)
            }
        }

        socket {
            // we use socket instead of http client to ensure we really send too big header
            // since a client may crash itself or do something unsuitable for the test here

            launch(Dispatchers.IO) {
                getOutputStream().writer().apply {
                    write("GET / HTTP/1.1\r\n")
                    write("Host: localhost:$port\r\n")
                    write("MyLongHeader: ")
                    repeat(10000) {
                        write("Abc")
                    }
                    write("\r\n\r\n")
                }
            }

            getInputStream().bufferedReader().apply {
                // we are expecting either 400 BadRequest or 200 OK Hello, World
                val status = readLine().trim().split(" ")[1].toInt()
                assert(status in listOf(200, 400, 431)) {
                    "status should be either 200 or 400 or 431 but it's $status"
                }

                val contentLength = parseHeadersAndGetContentLength()

                if (contentLength == -1) {
                    assertEquals(-1, read())
                } else {
                    skipHttpResponseContent(contentLength)
                }
            }
        }
    }

    class CustomFail(message: String) : Throwable(message)

    @Test
    fun testErrorInApplicationCallPipelineInterceptor() = runTest {
        val exceptions = mutableListOf<Throwable>()
        val loggerDelegate = LoggerFactory.getLogger("io.ktor.test")
        val logger = object : Logger by loggerDelegate {
            override fun error(message: String?, cause: Throwable?) {
                exceptions.add(cause!!)
            }
        }
        ApplicationCallPipeline(environment = createTestEnvironment()).items
            // fallback will reply with 404 and not 500
            .filter { it != ApplicationCallPipeline.Fallback }
            .forEach { phase ->
                val server = createServer(log = logger) {
                    intercept(phase) {
                        throw CustomFail("Failed in phase $phase")
                    }

                    routing {
                        get("/") {
                            call.respond("SUCCESS")
                        }
                    }
                }
                startServer(server)

                withUrl("/", {
                    retry {
                        noRetry()
                    }
                }) {
                    assertEquals(HttpStatusCode.InternalServerError, status, "Failed in phase $phase")
                    assertEquals(exceptions.size, 1, "Failed in phase $phase")
                    assertEquals("Failed in phase $phase", exceptions[0].message)
                    exceptions.clear()
                }

                server.stop(1000, 5000, TimeUnit.MILLISECONDS)
            }
    }

    @Test
    fun testErrorInApplicationReceivePipelineInterceptor() = runTest {
        val exceptions = mutableListOf<Throwable>()
        val loggerDelegate = LoggerFactory.getLogger("io.ktor.test")
        val logger = object : Logger by loggerDelegate {
            override fun error(message: String?, cause: Throwable?) {
                exceptions.add(cause!!)
            }
        }
        ApplicationReceivePipeline().items
            .forEach { phase ->
                val server = createServer(log = logger) {
                    intercept(ApplicationCallPipeline.Setup) {
                        call.request.pipeline.intercept(phase) { throw IllegalStateException("Failed in phase $phase") }
                    }

                    routing {
                        post("/") {
                            val body = call.receive<String>()
                            call.respond("SUCCESS $body")
                        }
                    }
                }
                startServer(server)

                withUrl(
                    "/",
                    {
                        method = HttpMethod.Post
                        setBody("body")
                        retry { noRetry() }
                    }
                ) {
                    assertEquals(HttpStatusCode.InternalServerError, status, "Failed in phase $phase")
                    assertEquals(exceptions.size, 1, "Failed in phase $phase")
                    assertEquals("Failed in phase $phase", exceptions[0].message)
                    exceptions.clear()
                }

                server.stop(1000, 5000, TimeUnit.MILLISECONDS)
            }
    }

    @Test
    fun testErrorInApplicationSendPipelineInterceptor() = runTest {
        val exceptions = mutableListOf<Throwable>()
        val loggerDelegate = LoggerFactory.getLogger("ktor.test")
        val logger = object : Logger by loggerDelegate {
            override fun error(message: String?, cause: Throwable?) {
                exceptions.add(cause!!)
            }
        }
        ApplicationSendPipeline().items
            .filter { it != ApplicationSendPipeline.Engine }
            .forEach { phase ->
                var intercepted = false
                val server = createServer(log = logger) {
                    intercept(ApplicationCallPipeline.Setup) setup@{
                        call.response.pipeline.intercept(phase) {
                            if (intercepted) return@intercept
                            intercepted = true
                            throw IllegalStateException("Failed in phase $phase")
                        }
                    }

                    routing {
                        get("/") {
                            call.respond("SUCCESS")
                        }
                    }
                }
                startServer(server)

                withUrl("/", {
                    retry {
                        noRetry()
                    }

                    intercepted = false
                }) {
                    val responseText = bodyAsText()
                    assertEquals(HttpStatusCode.InternalServerError, status, "Failed in phase $phase")
                    assertEquals(exceptions.size, 1, "Failed in phase $phase")
                    assertEquals("Failed in phase $phase", exceptions[0].message)
                    assertTrue(responseText.contains("Failed in phase"))
                    exceptions.clear()
                }

                server.stop(1000, 5000, TimeUnit.MILLISECONDS)
            }
    }

    @Test
    open fun testErrorInEnginePipelineInterceptor() = runTest {
        val exceptions = mutableListOf<Throwable>()
        val loggerDelegate = LoggerFactory.getLogger("ktor.test")
        val logger = object : Logger by loggerDelegate {
            override fun error(message: String?, cause: Throwable?) {
                exceptions.add(cause!!)
            }
        }

        val server = createServer(log = logger) {
            routing {
                get("/req") {
                    call.respond("SUCCESS")
                }
            }
        }

        val phase = EnginePipeline.Before
        val pipeline = (server.engine as BaseApplicationEngine).pipeline
        pipeline.intercept(phase) {
            throw IllegalStateException("Failed in engine pipeline")
        }
        startServer(server)

        withUrl("/req", {
            retry {
                noRetry()
            }
        }) {
            assertEquals(HttpStatusCode.InternalServerError, status, "Failed in engine pipeline")
            assertEquals(1, exceptions.size, "Failed in phase $phase")
            assertEquals("Failed in engine pipeline", exceptions[0].message)
            exceptions.clear()
        }

        server.stop(1000, 5000, TimeUnit.MILLISECONDS)
    }

    @OptIn(InternalAPI::class)
    @Test
    fun testRespondBlockingLarge() = runTest {
        val server = createServer {
            routing {
                get("/blocking/large") {
                    call.respondTextWriter(ContentType.Text.Plain, HttpStatusCode.OK) {
                        repeat(10000) {
                            write("large string ")
                        }
                    }
                }
            }
        }
        startServer(server)

        withUrl("/blocking/large") {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(ContentType.Text.Plain, contentType()?.withoutParameters())
            val result = rawContent.toInputStream().crcWithSize()
            assertEquals(10000 * 13L, result.second)
        }
    }

    @Test
    fun testDoubleHost() = runTest {
        createAndStartServer {
            get("/") {
                call.respond("OK")
            }
        }

        socket {
            val content = """
                GET / HTTP/1.1
                Host: www.example.com
                Host: www.example2.com


            """.trimIndent()

            outputStream.bufferedWriter().apply {
                write(content)
                flush()
            }

            val response = inputStream.bufferedReader()
            val status = response.readLine()

            assertContains(status, "400")
            outputStream.close()
        }
    }

    @Test
    fun testBodySmallerThanContentLength() = runTest {
        var failCause: Throwable? = null
        val result = Job()

        createAndStartServer {
            install(RequestValidation) {
                validateContentLength()
            }

            post("/") {
                try {
                    call.receive<ByteArray>().size
                } catch (cause: Throwable) {
                    failCause = cause
                } finally {
                    runCatching {
                        call.respond("OK")
                    }
                    result.complete()
                }
            }
        }

        socket {
            val request = buildString {
                append("POST / HTTP/1.1\r\n")
                append("Content-Length: 4\r\n")
                append("Content-Type: text/plain\r\n")
                append("Connection: close\r\n")
                append("Host: localhost\r\n")
                append("\r\n")
                append("ABC")
            }

            outputStream.writer().use {
                it.write(request)
            }
        }

        result.join()

        assertTrue(failCause != null)
        assertIs<IOException>(failCause)
    }

    @Test
    fun testOnCallRespondException() = runTest {
        var loggedException: Throwable? = null
        val log = object : AbstractLogger() {
            override fun isTraceEnabled(): Boolean = false
            override fun isTraceEnabled(marker: Marker?): Boolean = false
            override fun isDebugEnabled(): Boolean = false
            override fun isDebugEnabled(marker: Marker?): Boolean = false
            override fun isInfoEnabled(): Boolean = false
            override fun isInfoEnabled(marker: Marker?): Boolean = false
            override fun isWarnEnabled(): Boolean = false
            override fun isWarnEnabled(marker: Marker?): Boolean = false

            override fun isErrorEnabled(): Boolean = true

            override fun isErrorEnabled(marker: Marker?): Boolean = true

            override fun getFullyQualifiedCallerName(): String = "TEST"

            override fun handleNormalizedLoggingCall(
                level: Level?,
                marker: Marker?,
                messagePattern: String?,
                arguments: Array<out Any>?,
                throwable: Throwable?
            ) {
                loggedException = throwable
            }
        }

        createAndStartServer(log = log) {
            application.install(
                createApplicationPlugin("MyPlugin") {
                    onCallRespond { _ ->
                        error("oh nooooo")
                    }
                }
            )

            get {
                call.respondText("hello world")
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.InternalServerError, status)
            assertNotNull(loggedException)
            loggedException = null
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    open fun validateCallCoroutineContext() = runTest {
        val threadPool = Executors.newSingleThreadExecutor { r -> Thread(r, "Custom thread") }
        val dispatcher = threadPool.asCoroutineDispatcher()
        createAndStartServer {
            get {
                val applicationJob = application.coroutineContext.job
                val handlerJob = currentCoroutineContext().job
                val callJob = call.coroutineContext.job
                val jobsOutOfHierarchy: String? = listOfNotNull(
                    "call job".takeIf { applicationJob !in generateSequence(callJob) { it.parent }.toList() },
                    "handler job".takeIf { applicationJob !in generateSequence(handlerJob) { it.parent }.toList() },
                ).firstOrNull()
                val initialThread = Thread.currentThread()
                withContext(dispatcher) { delay(100) }
                val threadDiff = Thread.currentThread().let { currentThread ->
                    "${initialThread.name} --> ${currentThread.name}"
                        .takeIf { currentThread !== initialThread }
                }

                call.respondText(
                    """
                    Hierarchy preserved: ${jobsOutOfHierarchy ?: "true"}
                    Thread unchanged: ${threadDiff ?: "true"}
                    """.trimIndent()
                )
            }
        }

        try {
            withUrl("") {
                val bodyText = body<String>()
                assertEquals(
                    """
                    Hierarchy preserved: true
                    Thread unchanged: true
                    """.trimIndent(),
                    bodyText
                )
            }
        } finally {
            dispatcher.close()
            threadPool.shutdownNow()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    open fun testJobsAreCancelledOnShutdown() = runTest {
        var applicationJob: Job? = null
        var routingJob: Job? = null
        val jobsStartedLatch = CountDownLatch(2)

        suspend fun waitForever() {
            jobsStartedLatch.countDown()
            delay(Long.MAX_VALUE) // Hang until canceled
        }

        val server = createAndStartServer {
            // Launch a coroutine in the application context
            applicationJob = application.launch { waitForever() }

            // Configure a route that launches a coroutine in the routing context
            get("/launch-job") {
                routingJob = call.launch { waitForever() }
                call.respondText("Job launched")
            }
        }

        // Trigger the route to start the routing job
        withUrl("/launch-job") {
            assertEquals("Job launched", call.body<String>())
        }

        // Wait for both jobs to start
        assertTrue(jobsStartedLatch.await(5, TimeUnit.SECONDS), "Jobs did not start within timeout")

        // Verify both jobs are active
        assertNotNull(applicationJob, "Application job should not be null")
        assertNotNull(routingJob, "Routing job should not be null")
        assertTrue(applicationJob.isActive, "Application job should be active")
        assertTrue(routingJob.isActive, "Routing job should be active")

        // Stop the server
        server.stop(1, 10, TimeUnit.SECONDS)

        // Verify both jobs are canceled
        assertTrue(applicationJob.isCancelled, "Application job should be canceled")
        assertTrue(routingJob.isCancelled, "Routing job should be canceled")
    }
}

internal inline fun assertFails(block: () -> Unit) {
    assertFailsWith<Throwable>(block)
}
