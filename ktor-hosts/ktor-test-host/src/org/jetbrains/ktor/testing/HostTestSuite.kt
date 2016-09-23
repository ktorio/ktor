package org.jetbrains.ktor.testing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import org.junit.runners.model.*
import java.io.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import java.util.zip.*
import kotlin.concurrent.*
import kotlin.test.*

abstract class HostTestSuite<H : ApplicationHost> : HostTestBase<H>() {
    @Test
    fun testTextContent() {
        createAndStartServer() {
            handle {
                call.respond(TextContent(ContentType.Text.Plain, "test"))
            }
        }

        withUrl("/") {
            assertEquals("test", inputStream.reader().readText())
        }
        withUrlHttp2("/") {
//            assertEquals("test", contentAsString)
        }
    }

    @Test
    fun testStream() {
        createAndStartServer() {
            handle {
                call.respondWrite {
                    write("ABC")
                    flush()
                    write("123")
                    flush()
                }
            }
        }

        withUrl("/") {
            assertEquals("ABC123", inputStream.reader().readText())
        }
    }

    @Test
    fun testRequestContentFormData() {
        createAndStartServer() {
            handle {
                call.respond(call.request.content.get<ValuesMap>().formUrlEncode())
            }
        }

        withUrl("/") {
            doOutput = true
            requestMethod = "POST"

            outputStream.bufferedWriter().use {
                valuesOf("a" to listOf("1")).formUrlEncodeTo(it)
            }

            assertEquals("a=1", inputStream.reader().readText())
        }

        withUrl("/") {
            doOutput = false
            requestMethod = "GET"

            assertEquals("", inputStream.reader().readText())
        }
    }

    @Test
    fun testStreamNoFlush() {
        createAndStartServer() {
            handle {
                call.respondWrite {
                    write("ABC")
                    write("123")
                }
            }
        }

        withUrl("/") {
            assertEquals("ABC123", inputStream.reader().readText())
        }
    }

    @Test
    fun testSendTextWithContentType() {
        createAndStartServer() {
            handle {
                call.respondText(ContentType.Text.Plain, "Hello")
            }
        }

        withUrl("/") {
            assertEquals("Hello", inputStream.reader().readText())
            assertTrue(ContentType.parse(getHeaderField(HttpHeaders.ContentType)).match(ContentType.Text.Plain))
        }
    }

    @Test
    fun testRedirect() {
        createAndStartServer() {
            handle {
                call.respondRedirect("http://localhost:${call.request.port()}/page", true)
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.MovedPermanently.value, responseCode)
        }
    }


    @Test
    fun testRedirectFromInterceptor() {
        createAndStartServer {
            application.intercept(ApplicationCallPipeline.Infrastructure) {
                call.respondRedirect("/2", true)
            }
        }

        withUrl("/1/") {
            assertEquals(HttpStatusCode.MovedPermanently.value, responseCode)

            assertEquals("/2", getHeaderField(HttpHeaders.Location))
        }
    }

    @Test
    fun testHeader() {
        createAndStartServer() {
            handle {
                call.response.headers.append(HttpHeaders.ETag, "test-etag")
                call.respondText(ContentType.Text.Plain, "Hello")
            }
        }

        withUrl("/") {
            assertEquals("test-etag", getHeaderField(HttpHeaders.ETag))
        }
    }

    @Test
    fun testCookie() {
        createAndStartServer() {
            handle {
                call.response.cookies.append("k1", "v1")
                call.respondText(ContentType.Text.Plain, "Hello")
            }
        }

        withUrl("/") {
            assertEquals("k1=v1; \$x-enc=URI_ENCODING", getHeaderField(HttpHeaders.SetCookie))
        }
    }

    @Test
    fun testStaticServe() {
        createAndStartServer() {
            route("/files/") {
                serveClasspathResources("org/jetbrains/ktor/testing")
            }
        }

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
    }

    @Test
    fun testStaticServeFromDir() {
        val targetClasses = listOf(File("target/classes"), File("ktor-core/target/classes")).first { it.exists() }
        val file = targetClasses.walkBottomUp().filter { it.extension == "class" }.first()
        testLog.trace("test file is $file")

        createAndStartServer() {
            route("/files/") {
                serveFileSystem(targetClasses)
            }
        }

        withUrl("/files/${file.toRelativeString(targetClasses).urlPath()}") {
            val bytes = inputStream.readBytes(8192)
            assertNotEquals(0, bytes.size)

            // class file signature
            assertEquals(0xca, bytes[0].toInt() and 0xff)
            assertEquals(0xfe, bytes[1].toInt() and 0xff)
            assertEquals(0xba, bytes[2].toInt() and 0xff)
            assertEquals(0xbe, bytes[3].toInt() and 0xff)
        }
        assertFailsWith(FileNotFoundException::class) {
            withUrl("/files/${file.toRelativeString(targetClasses).urlPath()}2") {
                inputStream.readBytes()
            }
        }
        assertFailsWith(FileNotFoundException::class) {
            withUrl("/wefwefwefw") {
                inputStream.readBytes()
            }
        }
    }

    @Test
    fun testLocalFileContent() {
        val file = listOf(File("src"), File("ktor-core/src")).first { it.exists() }.walkBottomUp().filter { it.extension == "kt" }.first()
        testLog.trace("test file is $file")

        createAndStartServer() {
            handle {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/") {
            assertEquals(file.readText(), inputStream.reader().readText())
        }
    }

    @Test
    fun testLocalFileContentWithCompression() {
        val file = listOf(File("src"), File("ktor-core/src")).first { it.exists() }.walkBottomUp().filter { it.extension == "kt" }.first()
        testLog.trace("test file is $file")

        createAndStartServer() {
            application.install(Compression)
            handle {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/") {
            addRequestProperty(HttpHeaders.AcceptEncoding, "gzip")
            assertEquals(file.readText(), GZIPInputStream(inputStream).reader().readText())
            assertEquals("gzip", getHeaderField(HttpHeaders.ContentEncoding))
        }
    }

    @Test
    fun testLocalFileContentRange() {
        val file = listOf(File("src"), File("ktor-core/src")).first { it.exists() }.walkBottomUp().filter { it.extension == "kt" && it.reader().use { it.read().toChar() == 'p' } }.first()
        testLog.trace("test file is $file")

        createAndStartServer() {
            application.install(PartialContentSupport)
            handle {
                call.respond(LocalFileContent(file))
            }
        }

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
        testLog.trace("test file is $file")

        createAndStartServer() {
            application.install(Compression)
            application.install(PartialContentSupport)

            handle {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/") {
            addRequestProperty(HttpHeaders.AcceptEncoding, "gzip")
            setRequestProperty(HttpHeaders.Range, RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(0, 0))).toString())

            assertEquals("p", inputStream.reader().readText()) // it should be no compression if range requested
        }
    }

    @Test
    fun testJarFileContent() {
        createAndStartServer() {
            handle {
                call.respond(call.resolveClasspathWithPath("java/util", "/ArrayList.class")!!)
            }
        }

        withUrl("/") {
            inputStream.buffered().use { it.readBytes() }.let { bytes ->
                assertNotEquals(0, bytes.size)

                // class file signature
                assertEquals(0xca, bytes[0].toInt() and 0xff)
                assertEquals(0xfe, bytes[1].toInt() and 0xff)
                assertEquals(0xba, bytes[2].toInt() and 0xff)
                assertEquals(0xbe, bytes[3].toInt() and 0xff)
            }
        }
    }

    @Test
    fun testURIContent() {
        createAndStartServer() {
            handle {
                call.respond(URIFileContent(javaClass.classLoader.getResources("java/util/ArrayList.class").toList().first()))
            }
        }

        withUrl("/") {
            inputStream.buffered().use { it.readBytes() }.let { bytes ->
                assertNotEquals(0, bytes.size)

                // class file signature
                assertEquals(0xca, bytes[0].toInt() and 0xff)
                assertEquals(0xfe, bytes[1].toInt() and 0xff)
                assertEquals(0xba, bytes[2].toInt() and 0xff)
                assertEquals(0xbe, bytes[3].toInt() and 0xff)
            }
        }
    }

    @Test
    fun testURIContentLocalFile() {
        val file = listOf(File("target/classes"), File("ktor-core/target/classes")).first { it.exists() }.walkBottomUp().filter { it.extension == "class" }.first()
        testLog.trace("test file is $file")

        createAndStartServer() {
            handle {
                call.respond(URIFileContent(file.toURI()))
            }
        }

        withUrl("/") {
            inputStream.buffered().use { it.readBytes() }.let { bytes ->
                assertNotEquals(0, bytes.size)

                // class file signature
                assertEquals(0xca, bytes[0].toInt() and 0xff)
                assertEquals(0xfe, bytes[1].toInt() and 0xff)
                assertEquals(0xba, bytes[2].toInt() and 0xff)
                assertEquals(0xbe, bytes[3].toInt() and 0xff)
            }
        }
    }

    @Test
    fun testPathComponentsDecoding() {
        createAndStartServer() {
            get("/a%20b") {
                call.respondText("space")
            }
            get("/a+b") {
                call.respondText("plus")
            }
        }

        withUrl("/a%20b") {
            assertEquals("space", inputStream.bufferedReader().use { it.readText() })
        }
        withUrl("/a+b") {
            assertEquals("plus", inputStream.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun testFormUrlEncoded() {
        createAndStartServer() {
            post("/") {
                call.respondText("${call.parameters["urlp"]},${call.parameters["formp"]}")
            }
        }

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
        createAndStartServer() {
            route("/echo") {
                handle {
                    val inChannel = call.request.content.get<ReadChannel>()
                    val buffer = ByteArrayWriteChannel()
                    val readFuture = CompletableFuture<Long>()

                    readFuture.whenComplete { size, throwable ->
                        if (throwable != null) {
                            failAndProceed(throwable)
                        }

                        call.response.status(HttpStatusCode.OK)
                        call.response.contentType(ContentType.Application.OctetStream)

                        runBlock {
                            call.respond(object : FinalContent.ChannelContent() {
                                override val headers: ValuesMap
                                    get() = ValuesMap.Empty

                                override fun channel() = ByteArrayReadChannel(buffer.toByteArray())
                            })
                        }
                    }

                    inChannel.copyToAsyncThenComplete(buffer, readFuture)
                    pause()
                }
            }
        }

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
        createAndStartServer() {
            post("/") {
                val text = call.request.content.get<ReadChannel>().asInputStream().bufferedReader().readText()
                call.response.status(HttpStatusCode.OK)
                call.respond(text)
            }
        }

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
    open fun testMultipartFileUpload() {
        createAndStartServer() {
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
        }

        withUrl("/") {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            setRequestProperty(HttpHeaders.ContentType, ContentType.MultiPart.FormData.withParameter("boundary", "***bbb***").toString())

            outputStream.bufferedWriter(Charsets.ISO_8859_1).let { out ->
                out.apply {
                    append("\r\n")
                    append("--***bbb***\r\n")
                    append("Content-Disposition: form-data; name=\"a story\"\r\n")
                    append("\r\n")
                    append("Hi user. The snake you gave me for free ate all the birds. Please take it back ASAP.\r\n")
                    append("--***bbb***\r\n")
                    append("Content-Disposition: form-data; name=\"attachment\"; filename=\"original.txt\"\r\n")
                    append("Content-Type: text/plain\r\n")
                    append("\r\n")
                    append("File content goes here\r\n")
                    append("--***bbb***--\r\n")
                    flush()
                }
            }

            assertEquals("a story=Hi user. The snake you gave me for free ate all the birds. Please take it back ASAP.\nfile:attachment,original.txt,File content goes here\n", inputStream.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun testRequestTwiceNoKeepAlive() {
        createAndStartServer() {
            get("/") {
                call.respond(TextContent(ContentType.Text.Plain, "Text"))
            }
        }

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
        createAndStartServer() {
            get("/") {
                call.respond(TextContent(ContentType.Text.Plain, "Text"))
            }
        }


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
        createAndStartServer() {
            post("/") {
                call.respond(call.request.content.get<String>())
            }
        }

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
        createAndStartServer() {
            post("/") {
                call.respond(call.request.content.get<InputStream>().reader().readText())
            }
        }

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
    fun testStatusCodeDirect() {
        createAndStartServer() {
            get("/") {
                call.response.status(HttpStatusCode.Found)
                call.respond("Hello")
            }
        }

        withUrl("/") {
            assertEquals("Hello", inputStream.reader().readText())
            assertEquals(HttpStatusCode.Found.value, responseCode)
        }
    }

    @Test
    fun testStatusCodeViaResponseObject() {
        createAndStartServer() {
            get("/") {
                call.respond(HttpStatusCode.Found)
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.Found.value, responseCode)
        }
    }

    @Test
    fun testStatusCodeViaResponseObject2() {
        createAndStartServer() {
            get("/") {
                call.respond(HttpStatusContent(HttpStatusCode.Found, "Hello"))
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.Found.value, responseCode)
        }
    }

    @Test
    fun test404() {
        createAndStartServer() {
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.NotFound.value, responseCode)
        }

        withUrl("/aaaa") {
            assertEquals(HttpStatusCode.NotFound.value, responseCode)
        }
    }

    @Test
    fun testProxyHeaders() {
        createAndStartServer() {
            install(XForwardedHeadersSupport)
            get("/") {
                call.respond(call.url { })
            }
        }

        withUrl("/") { port ->
            setRequestProperty(HttpHeaders.XForwardedHost, "my-host:90")

            val expectedProto = if (port == sslPort) "https" else "http"
            assertEquals("$expectedProto://my-host:90/", inputStream.reader().readText())
        }

        withUrl("/") { port ->
            setRequestProperty(HttpHeaders.XForwardedHost, "my-host")

            val expectedProto = if (port == sslPort) "https" else "http"
            assertEquals("$expectedProto://my-host/", inputStream.reader().readText())
        }

        withUrl("/") {
            setRequestProperty(HttpHeaders.XForwardedHost, "my-host:90")
            setRequestProperty(HttpHeaders.XForwardedProto, "https")

            assertEquals("https://my-host:90/", inputStream.reader().readText())
        }

        withUrl("/") {
            setRequestProperty(HttpHeaders.XForwardedHost, "my-host")
            setRequestProperty(HttpHeaders.XForwardedProto, "https")

            assertEquals("https://my-host/", inputStream.reader().readText())
        }
    }


    @Test
    fun testHostRequestParts() {
        createAndStartServer {
            get("/path/1") {
                call.respond(call.request.path())
            }
            get("/document/1") {
                call.respond(call.request.document())
            }
            get("/queryString/1") {
                call.respond(call.request.queryString())
            }
            get("/uri/1") {
                call.respond(call.request.uri)
            }
        }

        withUrl("/path/1?p=v") {
            assertEquals("/path/1", inputStream.reader().readText())
        }
        withUrl("/path/1?") {
            assertEquals("/path/1", inputStream.reader().readText())
        }
        withUrl("/path/1") {
            assertEquals("/path/1", inputStream.reader().readText())
        }

        withUrl("/document/1?p=v") {
            assertEquals("1", inputStream.reader().readText())
        }
        withUrl("/document/1?") {
            assertEquals("1", inputStream.reader().readText())
        }
        withUrl("/document/1") {
            assertEquals("1", inputStream.reader().readText())
        }

        withUrl("/queryString/1?p=v") {
            assertEquals("p=v", inputStream.reader().readText())
        }
        withUrl("/queryString/1?") {
            assertEquals("", inputStream.reader().readText())
        }
        withUrl("/queryString/1") {
            assertEquals("", inputStream.reader().readText())
        }

        withUrl("/uri/1?p=v") {
            assertEquals("/uri/1?p=v", inputStream.reader().readText())
        }
        withUrl("/uri/1?") {
            assertEquals("/uri/1?", inputStream.reader().readText())
        }
        withUrl("/uri/1") {
            assertEquals("/uri/1", inputStream.reader().readText())
        }
    }

    @Test
    fun testRequestParameters() {
        createAndStartServer {
            get("/*") {
                call.respond(call.request.queryParameters.getAll(call.request.path().removePrefix("/")).toString())
            }
        }

        withUrl("/single?single=value") {
            assertEquals("[value]", inputStream.reader().readText())
        }
        withUrl("/multiple?multiple=value1&multiple=value2") {
            assertEquals("[value1, value2]", inputStream.reader().readText())
        }
        withUrl("/missing") {
            assertEquals("null", inputStream.reader().readText())
        }
    }

    @Test
    fun testCallLoggerOnError() {
        val message = "expected, ${nextNonce()}"
        val collected = CopyOnWriteArrayList<Throwable>()

        createAndStartServer({
            val delegate = SLF4JApplicationLog("embedded")
            log = object : ApplicationLog by delegate {
                override val name = "DummyLogger"

                override fun fork(name: String) = this

                override fun error(message: String, exception: Throwable?) {
                    if (exception != null) {
                        collected.add(exception)
                    }

                }
            }
        }, {
            application.install(CallLogging)

            get("/") {
                throw ExpectedException(message)
            }
        })

        withUrl("/") {
            assertFailsWith<IOException> {
                inputStream.reader().readText()
            }

            assertEquals(message, collected.single { it is ExpectedException }.message)
            collected.clear()
        }
    }

    @Test(timeout = 30000L)
    fun testBlockingConcurrency() {
        val completed = AtomicInteger(0)

        createAndStartServer({
            executorServiceBuilder = {
                Executors.newScheduledThreadPool(3)
            }
        }, {
            get("/") {
                call.respondWrite {
                    try {
                        append("OK\n")
                    } finally {
                        completed.incrementAndGet()
                    }
                }
            }
        })

        val count = 100
        val latch = CountDownLatch(count)
        val errors = CopyOnWriteArrayList<Throwable>()

        for (i in 1..latch.count) {
            thread {
                val isBlocker = i % 10L == 0L

                try {
                    withUrl("/") {
                        inputStream.reader().use { reader ->
                            val firstByte = reader.read()
                            assertNotEquals(-1, firstByte)
                            assertEquals('O', firstByte.toChar())

                            if (isBlocker) {
                                Thread.sleep(1000)
                            } else {
                                assertEquals("K\n", reader.readText())
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

        assertEquals(count * 2, completed.get())
    }

    private fun String.urlPath() = replace("\\", "/")
    private class ExpectedException(message: String) : RuntimeException(message)
}