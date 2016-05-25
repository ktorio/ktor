package org.jetbrains.ktor.tests.application

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.routing.*
import org.junit.*
import java.io.*
import java.net.*
import java.util.concurrent.*
import java.util.zip.*
import kotlin.concurrent.*
import kotlin.test.*

abstract class HostTestSuite {
    private val port = findFreePort()
    private var server: ApplicationHost? = null

    abstract fun createServer(port: Int, block: Routing.() -> Unit): ApplicationHost

    @Before
    fun setUp() {
        println("Starting server on port $port")
    }

    @After
    fun tearDown() {
        server?.stop()
    }

    private fun startServer(server: ApplicationHost) {
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

    @Test
    fun testTextContent() {
        val server = createServer(port) {
            handle {
                call.respond(TextContent(ContentType.Text.Plain, "test"))
            }
        }
        startServer(server)

        withUrl("/") {
            assertEquals("test", inputStream.reader().readText())
        }
    }

    @Test
    fun testStream() {
        val server = createServer(port) {
            handle {
                call.respondWrite {
                    write("ABC")
                    flush()
                    write("123")
                    flush()
                }
            }
        }
        startServer(server)

        withUrl("/") {
            assertEquals("ABC123", inputStream.reader().readText())
        }
    }

    @Test
    fun testStreamNoFlush() {
        val server = createServer(port) {
            handle {
                call.respondWrite {
                    write("ABC")
                    write("123")
                }
            }
        }
        startServer(server)

        withUrl("/") {
            assertEquals("ABC123", inputStream.reader().readText())
        }
    }

    @Test
    fun testSendTextWithContentType() {
        val server = createServer(port) {
            handle {
                call.respondText(ContentType.Text.Plain, "Hello")
            }
        }
        startServer(server)

        withUrl("/") {
            assertEquals("Hello", inputStream.reader().readText())
            assertTrue(ContentType.parse(getHeaderField(HttpHeaders.ContentType)).match(ContentType.Text.Plain))
        }
    }

    @Test
    fun testRedirect() {
        val server = createServer(port) {
            handle {
                call.respondRedirect("http://localhost:$port/page", true)
            }
        }
        startServer(server)

        withUrl("/") {
            assertEquals(HttpStatusCode.MovedPermanently.value, responseCode)
        }
    }

    @Test
    fun testHeader() {
        val server = createServer(port) {
            handle {
                call.response.headers.append(HttpHeaders.ETag, "test-etag")
                call.respondText(ContentType.Text.Plain, "Hello")
            }
        }
        startServer(server)

        withUrl("/") {
            assertEquals("test-etag", getHeaderField(HttpHeaders.ETag))
        }
    }

    @Test
    fun testCookie() {
        val server = createServer(port) {
            handle {
                call.response.cookies.append("k1", "v1")
                call.respondText(ContentType.Text.Plain, "Hello")
            }
        }
        startServer(server)

        withUrl("/") {
            assertEquals("k1=v1; \$x-enc=URI_ENCODING", getHeaderField(HttpHeaders.SetCookie))
        }
    }

    @Test
    fun testStaticServe() {
        val server = createServer(port) {
            route("/files/") {
                serveClasspathResources("org/jetbrains/ktor/tests/application")
            }
        }

        startServer(server)

        try {
            withUrl("/files/${HostTestSuite::class.simpleName}.class") {
                val bytes = inputStream.readBytes(8192)
                assertNotEquals(0, bytes.size)

                // class file signature
                assertEquals(0xca, bytes[0].toInt() and 0xff)
                assertEquals(0xfe, bytes[1].toInt() and 0xff)
                assertEquals(0xba, bytes[2].toInt() and 0xff)
                assertEquals(0xbe, bytes[3].toInt() and 0xff)
            }
            assertFailsWith(FileNotFoundException::class) {
                withUrl("/files/${HostTestSuite::class.simpleName}.class2") {
                    inputStream.readBytes()
                }
            }
            assertFailsWith(FileNotFoundException::class) {
                withUrl("/wefwefwefw") {
                    inputStream.readBytes()
                }
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun testStaticServeFromDir() {
        val targetClasses = listOf(File("target/classes"), File("ktor-core/target/classes")).first { it.exists() }
        val file = targetClasses.walkBottomUp().filter { it.extension == "class" }.first()
        println("test file is $file")

        val server = createServer(port) {
            route("/files/") {
                serveFileSystem(targetClasses)
            }
        }

        startServer(server)

        try {
            withUrl("/files/${file.toRelativeString(targetClasses)}") {
                val bytes = inputStream.readBytes(8192)
                assertNotEquals(0, bytes.size)

                // class file signature
                assertEquals(0xca, bytes[0].toInt() and 0xff)
                assertEquals(0xfe, bytes[1].toInt() and 0xff)
                assertEquals(0xba, bytes[2].toInt() and 0xff)
                assertEquals(0xbe, bytes[3].toInt() and 0xff)
            }
            assertFailsWith(FileNotFoundException::class) {
                withUrl("/files/${file.toRelativeString(targetClasses)}2") {
                    inputStream.readBytes()
                }
            }
            assertFailsWith(FileNotFoundException::class) {
                withUrl("/wefwefwefw") {
                    inputStream.readBytes()
                }
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun testLocalFileContent() {
        val file = listOf(File("src"), File("ktor-core/src")).first{ it.exists() }.walkBottomUp().filter { it.extension == "kt" }.first()
        println("test file is $file")

        val server = createServer(port) {
            handle {
                call.respond(LocalFileContent(file))
            }
        }
        startServer(server)

        withUrl("/") {
            assertEquals(file.readText(), inputStream.reader().readText())
        }
    }

    @Test
    fun testLocalFileContentWithCompression() {
        val file = listOf(File("src"), File("ktor-core/src")).first{ it.exists() }.walkBottomUp().filter { it.extension == "kt" }.first()
        println("test file is $file")

        val server = createServer(port) {
            application.install(CompressionSupport)
            handle {
                call.respond(LocalFileContent(file))
            }
        }
        startServer(server)

        withUrl("/") {
            addRequestProperty(HttpHeaders.AcceptEncoding, "gzip")
            assertEquals(file.readText(), GZIPInputStream(inputStream).reader().readText())
            assertEquals("gzip", getHeaderField(HttpHeaders.ContentEncoding))
        }
    }

    @Test
    fun testLocalFileContentRange() {
        val file = listOf(File("src"), File("ktor-core/src")).first { it.exists() }.walkBottomUp().filter { it.extension == "kt" && it.reader().use { it.read().toChar() == 'p' } }.first()
        println("test file is $file")

        val server = createServer(port) {
            application.install(PartialContentSupport)
            handle {
                call.respond(LocalFileContent(file))
            }
        }
        startServer(server)

        withUrl("/") {
            setRequestProperty(HttpHeaders.Range, RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(0, 0))).toString())
            assertEquals("p", inputStream.reader().readText())
        }
        withUrl("/") {
            setRequestProperty(HttpHeaders.Range, RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(1, 2))).toString())
            assertEquals("ac", inputStream.reader().readText())
        }
    }

    @Test
    fun testLocalFileContentRangeWithCompression() {
        val file = listOf(File("src"), File("ktor-core/src")).first { it.exists() }.walkBottomUp().filter { it.extension == "kt" && it.reader().use { it.read().toChar() == 'p' } }.first()
        println("test file is $file")

        val server = createServer(port) {
            application.install(CompressionSupport)
            application.install(PartialContentSupport)

            handle {
                call.respond(LocalFileContent(file))
            }
        }
        startServer(server)

        withUrl("/") {
            addRequestProperty(HttpHeaders.AcceptEncoding, "gzip")
            setRequestProperty(HttpHeaders.Range, RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(0, 0))).toString())

            assertEquals("p", inputStream.reader().readText()) // it should be no compression if range requested
        }
    }

    @Test
    fun testJarFileContent() {
        val server = createServer(port) {
            handle {
                call.respond(call.resolveClasspathWithPath("java/util", "/ArrayList.class")!!)
            }
        }
        startServer(server)

        URL("http://127.0.0.1:$port/").openStream().buffered().use { it.readBytes() }.let { bytes ->
            assertNotEquals(0, bytes.size)

            // class file signature
            assertEquals(0xca, bytes[0].toInt() and 0xff)
            assertEquals(0xfe, bytes[1].toInt() and 0xff)
            assertEquals(0xba, bytes[2].toInt() and 0xff)
            assertEquals(0xbe, bytes[3].toInt() and 0xff)
        }
    }

    @Test
    fun testURIContent() {
        val server = createServer(port) {
            handle {
                call.respond(URIFileContent(javaClass.classLoader.getResources("java/util/ArrayList.class").toList().first()))
            }
        }
        startServer(server)

        URL("http://127.0.0.1:$port/").openStream().buffered().use { it.readBytes() }.let { bytes ->
            assertNotEquals(0, bytes.size)

            // class file signature
            assertEquals(0xca, bytes[0].toInt() and 0xff)
            assertEquals(0xfe, bytes[1].toInt() and 0xff)
            assertEquals(0xba, bytes[2].toInt() and 0xff)
            assertEquals(0xbe, bytes[3].toInt() and 0xff)
        }
    }

    @Test
    fun testURIContentLocalFile() {
        val file = listOf(File("target/classes"), File("ktor-core/target/classes")).first { it.exists() }.walkBottomUp().filter { it.extension == "class" }.first()
        println("test file is $file")

        val server = createServer(port) {
            handle {
                call.respond(URIFileContent(file.toURI()))
            }
        }
        startServer(server)

        URL("http://127.0.0.1:$port/").openStream().buffered().use { it.readBytes() }.let { bytes ->
            assertNotEquals(0, bytes.size)

            // class file signature
            assertEquals(0xca, bytes[0].toInt() and 0xff)
            assertEquals(0xfe, bytes[1].toInt() and 0xff)
            assertEquals(0xba, bytes[2].toInt() and 0xff)
            assertEquals(0xbe, bytes[3].toInt() and 0xff)
        }
    }

    @Test
    fun testPathComponentsDecoding() {
        val server = createServer(port) {
            get("/a%20b") {
                call.respondText("space")
            }
            get("/a+b") {
                call.respondText("plus")
            }
        }
        startServer(server)

        withUrl("/a%20b") {
            assertEquals("space", inputStream.bufferedReader().use { it.readText() })
        }
        withUrl("/a+b") {
            assertEquals("plus", inputStream.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun testFormUrlEncoded() {
        val server = createServer(port) {
            post("/") {
                call.respondText("${call.request.parameter("urlp")},${call.request.parameter("formp")}")
            }
        }
        startServer(server)

        withUrl("/?urlp=1") {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            setRequestProperty(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())

            outputStream.use { out ->
                out.writer().apply {
                    append("formp=2")
                    flush()
                }
            }

            assertEquals(HttpStatusCode.OK.value, responseCode)
            assertEquals("1,2", inputStream.bufferedReader().readText())
        }
    }

    @Test
    fun testRequestBodyAsyncEcho() {
        val server = createServer(port) {
            route("/echo") {
                handle {
                    val inChannel = call.request.content.get<AsyncReadChannel>()
                    val buffer = ByteArrayAsyncWriteChannel()
                    val readFuture = CompletableFuture<Long>()

                    readFuture.whenComplete { size, throwable ->
                        if (throwable != null) {
                            failAndProceed(throwable)
                        }

                        call.response.status(HttpStatusCode.OK)
                        call.response.contentType(ContentType.Application.OctetStream)
                        val outChannel = call.response.channel()

                        val writeFuture = CompletableFuture<Long>()
                        writeFuture.whenComplete { size, throwable ->
                            if (throwable != null) {
                                failAndProceed(throwable)
                            }

                            call.close()
                            finishAllAndProceed()
                        }

                        ByteArrayAsyncReadChannel(buffer.toByteArray()).copyToAsyncThenComplete(outChannel, writeFuture)
                    }

                    inChannel.copyToAsyncThenComplete(buffer, readFuture)
                    pause()
                }
            }
        }
        startServer(server)

        withUrl("/echo") {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            setRequestProperty(HttpHeaders.ContentType, ContentType.Text.Plain.toString())

            outputStream.use { out ->
                out.writer().apply {
                    append("POST test\n")
                    append("Another line")
                    flush()
                }
            }

            assertEquals("POST test\nAnother line", inputStream.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun testEchoBlocking() {
        startServer(createServer(port) {
            post("/") {
                val text = call.request.content.get<AsyncReadChannel>().asInputStream().bufferedReader().readText()
                call.response.status(HttpStatusCode.OK)
                call.response.channel().asOutputStream().bufferedWriter().use {
                    it.write(text)
                    it.flush()
                }

                call.close()
                finishAll()
            }
        })

        withUrl("/") {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            setRequestProperty(HttpHeaders.ContentType, ContentType.Text.Plain.toString())

            outputStream.use { out ->
                out.writer().apply {
                    append("POST content")
                    flush()
                }
            }

            assertEquals("POST content", inputStream.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun testMultipartFileUpload() {
        startServer(createServer(port) {
            post("/") {
                thread {
                    runBlockWithResult {
                        val response = StringBuilder()

                        call.request.content.get<MultiPartData>().parts.sortedBy { it.partName }.forEach { part ->
                            when (part) {
                                is PartData.FormItem -> response.append("${part.partName}=${part.value}\n")
                                is PartData.FileItem -> response.append("file:${part.partName},${part.originalFileName},${part.streamProvider().bufferedReader().readText()}\n")
                            }

                            part.dispose()
                        }

                        call.respondText(response.toString())
                    }
                }
                pause()
            }
        })

        withUrl("/") {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            setRequestProperty(HttpHeaders.ContentType, ContentType.MultiPart.FormData.withParameter("boundary", "***bbb***").toString())

            outputStream.bufferedWriter().use { out ->
                out.apply {
                    append("\n")
                    append("--***bbb***\n")
                    append("Content-Disposition: form-data; name=\"a story\"\n")
                    append("\n")
                    append("Hi user. The snake you gave me for free ate all the birds. Please take it back ASAP.\n")
                    append("--***bbb***\n")
                    append("Content-Disposition: form-data; name=\"attachment\"; filename=\"original.txt\"\n")
                    append("Content-Type: text/plain\n")
                    append("\n")
                    append("File content goes here\n")
                    append("--***bbb***--\n")
                    flush()
                }
            }

            assertEquals("a story=Hi user. The snake you gave me for free ate all the birds. Please take it back ASAP.\nfile:attachment,original.txt,File content goes here\n", inputStream.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun testRequestTwiceNoKeepAlive() {
        startServer(createServer(port) {
            get("/") {
                call.respond(TextContent(ContentType.Text.Plain, "Text"))
            }
        })

        withUrl("/") {
            setRequestProperty(HttpHeaders.Connection, "close")
            assertEquals("Text", inputStream.bufferedReader().readText())
        }

        withUrl("/") {
            setRequestProperty(HttpHeaders.Connection, "close")
            assertEquals("Text", inputStream.bufferedReader().readText())
        }
    }

    @Test
    fun testRequestTwiceWithKeepAlive() {
        startServer(createServer(port) {
            get("/") {
                call.respond(TextContent(ContentType.Text.Plain, "Text"))
            }
        })


        withUrl("/") {
            setRequestProperty(HttpHeaders.Connection, "keep-alive")
            assertEquals("Text", inputStream.bufferedReader().readText())
        }

        withUrl("/") {
            setRequestProperty(HttpHeaders.Connection, "keep-alive")
            assertEquals("Text", inputStream.bufferedReader().readText())
        }
    }

    @Test
    fun testRequestContentString() {
        startServer(createServer(port) {
            post("/") {
                call.respond(call.request.content.get<String>())
            }
        })

        withUrl("/") {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty(HttpHeaders.ContentType, ContentType.Text.Plain.toString())

            outputStream.use {
                it.write("Hello".toByteArray())
            }

            assertEquals("Hello", inputStream.reader().readText())
        }
    }

    @Test
    fun testRequestContentInputStream() {
        startServer(createServer(port) {
            post("/") {
                call.respond(call.request.content.get<InputStream>().reader().readText())
            }
        })

        withUrl("/") {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty(HttpHeaders.ContentType, ContentType.Text.Plain.toString())

            outputStream.use {
                it.write("Hello".toByteArray())
            }

            assertEquals("Hello", inputStream.reader().readText())
        }
    }

    fun findFreePort() = ServerSocket(0).use { it.localPort }
    fun withUrl(path: String, block: HttpURLConnection.() -> Unit) {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = false

        connection.block()
    }

    private fun PipelineContext<*>.failAndProceed(e: Throwable): Nothing {
        runBlock { fail(e) }
    }

    private fun PipelineContext<*>.finishAllAndProceed(): Nothing {
        runBlock { finishAll() }
    }
}