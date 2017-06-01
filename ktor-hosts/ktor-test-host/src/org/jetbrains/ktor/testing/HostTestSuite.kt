package org.jetbrains.ktor.testing

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import org.junit.runners.model.*
import java.io.*
import java.net.*
import java.security.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import java.util.zip.*
import kotlin.concurrent.*
import kotlin.test.*

abstract class HostTestSuite<THost : ApplicationHost>(hostFactory: ApplicationHostFactory<THost>) : HostTestBase<THost>(hostFactory) {
    @Test
    fun testTextContent() {
        createAndStartServer {
            handle {
                call.respond(TextContent("test", ContentType.Text.Plain.withCharset(Charsets.UTF_8)))
            }
        }

        withUrl("/") {
            val fields = headerFields.toMutableMap()
            fields.remove(null) // Remove response line HTTP/1.1 200 OK since it's not a header
            fields.remove("Date") // Do not check for Date field since it's unstable

            // Check content type manually because spacing and case can be different per host
            val contentType = fields.remove("Content-Type")?.single()
            assertNotNull(contentType) // Content-Type should be present
            val parsedContentType = ContentType.parse(contentType!!) // It should parse
            assertEquals(ContentType.Text.Plain, parsedContentType.withoutParameters())
            assertEquals(Charsets.UTF_8, parsedContentType.charset())

            assertEquals(mapOf(
                    "Connection" to listOf("keep-alive"),
                    "Content-Length" to listOf("4")), fields)

            assertEquals(200, responseCode)
            assertEquals("test", inputStream.reader().use { it.readText() })
        }
        withUrlHttp2("/") {
            //            assertEquals("test", contentAsString)
        }
    }

    @Test
    fun testStream() {
        createAndStartServer {
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
            assertEquals(200, responseCode)
            assertEquals("ABC123", inputStream.reader().use { it.readText() })
        }
    }

    @Test
    fun testInternalServerErrorWithoutCallLog() {
        createAndStartServer {
            application.uninstall(CallLogging)
            handle {
                throw Exception("Boom!")
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.InternalServerError.value, responseCode)
        }
    }

    @Test
    fun testInternalServerError() {
        createAndStartServer {
            handle {
                throw Exception("Boom!")
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.InternalServerError.value, responseCode)
        }
    }

    @Test
    fun testRequestContentFormData() {
        createAndStartServer {
            handle {
                call.respond(call.request.receive<ValuesMap>().formUrlEncode())
            }
        }

        withUrl("/") {
            doOutput = true
            requestMethod = "POST"

            outputStream.bufferedWriter().use {
                valuesOf("a" to listOf("1")).formUrlEncodeTo(it)
            }

            assertEquals(200, responseCode)
            assertEquals("a=1", inputStream.reader().use { it.readText() })
        }

        withUrl("/") {
            doOutput = false
            requestMethod = "GET"

            assertEquals(200, responseCode)
            assertEquals("", inputStream.reader().use { it.readText() })
        }
    }

    @Test
    fun testStreamNoFlush() {
        createAndStartServer {
            handle {
                call.respondWrite {
                    write("ABC")
                    write("123")
                }
            }
        }

        withUrl("/") {
            assertEquals(200, responseCode)
            assertEquals("ABC123", inputStream.reader().use { it.readText() })
        }
    }

    @Test
    fun testSendTextWithContentType() {
        createAndStartServer {
            handle {
                call.respondText("Hello", ContentType.Text.Plain)
            }
        }

        withUrl("/") {
            assertEquals(200, responseCode)
            assertEquals("Hello", inputStream.reader().use { it.readText() })
            assertTrue(ContentType.parse(getHeaderField(HttpHeaders.ContentType)).match(ContentType.Text.Plain))
        }
    }

    @Test
    fun testRedirect() {
        createAndStartServer {
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
        createAndStartServer {
            handle {
                call.response.headers.append(HttpHeaders.ETag, "test-etag")
                call.respondText("Hello", ContentType.Text.Plain)
            }
        }

        withUrl("/") {
            assertEquals(200, responseCode)
            assertEquals("test-etag", getHeaderField(HttpHeaders.ETag))
        }
    }

    @Test
    fun testCookie() {
        createAndStartServer {
            handle {
                call.response.cookies.append("k1", "v1")
                call.respondText("Hello", ContentType.Text.Plain)
            }
        }

        withUrl("/") {
            assertEquals(200, responseCode)
            assertEquals("k1=v1; \$x-enc=URI_ENCODING", getHeaderField(HttpHeaders.SetCookie))
        }
    }

    @Test
    fun testStaticServe() {
        createAndStartServer {
            static("/files/") {
                resources("org/jetbrains/ktor/testing")
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

        createAndStartServer {
            static("/files") {
                files(targetClasses.path)
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

        createAndStartServer {
            handle {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/") {
            assertEquals(200, responseCode)
            assertEquals(file.readText(), inputStream.reader().use { it.readText() })
        }
    }

    @Test
    fun testLocalFileContentWithCompression() {
        val file = listOf(File("src"), File("ktor-core/src")).first { it.exists() }.walkBottomUp().filter { it.extension == "kt" }.first()
        testLog.trace("test file is $file")

        createAndStartServer {
            application.install(Compression)
            handle {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/") {
            addRequestProperty(HttpHeaders.AcceptEncoding, "gzip")

            assertEquals(200, responseCode)
            assertEquals(file.readText(), GZIPInputStream(inputStream).reader().use { it.readText() })
            assertEquals("gzip", getHeaderField(HttpHeaders.ContentEncoding))
        }
    }

    @Test
    fun testLocalFileContentRange() {
        val file = listOf(File("src"), File("ktor-core/src")).first { it.exists() }.walkBottomUp().filter { it.extension == "kt" && it.reader().use { it.read().toChar() == 'p' } }.first()
        testLog.trace("test file is $file")

        createAndStartServer {
            application.install(PartialContentSupport)
            handle {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/") {
            setRequestProperty(HttpHeaders.Range, RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(0, 0))).toString())

            assertEquals(HttpStatusCode.PartialContent.value, responseCode)
            assertEquals("p", inputStream.reader().use { it.readText() })
        }
        withUrl("/") {
            setRequestProperty(HttpHeaders.Range, RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(1, 2))).toString())

            assertEquals(HttpStatusCode.PartialContent.value, responseCode)
            assertEquals("ac", inputStream.reader().use { it.readText() })
        }
    }

    @Test
    fun testLocalFileContentRangeWithCompression() {
        val file = listOf(File("src"), File("ktor-core/src")).first { it.exists() }.walkBottomUp().filter { it.extension == "kt" && it.reader().use { it.read().toChar() == 'p' } }.first()
        testLog.trace("test file is $file")

        createAndStartServer {
            application.install(Compression)
            application.install(PartialContentSupport)

            handle {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/") {
            addRequestProperty(HttpHeaders.AcceptEncoding, "gzip")
            setRequestProperty(HttpHeaders.Range, RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(0, 0))).toString())

            assertEquals(HttpStatusCode.PartialContent.value, responseCode)
            assertEquals("p", inputStream.reader().use { it.readText() }) // it should be no compression if range requested
        }
    }

    @Test
    fun testJarFileContent() {
        createAndStartServer {
            handle {
                call.respond(call.resolveResource("/ArrayList.class", "java.util")!!)
            }
        }

        withUrl("/") {
            assertEquals(200, responseCode)
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
        createAndStartServer {
            handle {
                call.respond(URIFileContent(this::class.java.classLoader.getResources("java/util/ArrayList.class").toList().first()))
            }
        }

        withUrl("/") {
            assertEquals(200, responseCode)
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

        createAndStartServer {
            handle {
                call.respond(URIFileContent(file.toURI()))
            }
        }

        withUrl("/") {
            assertEquals(200, responseCode)
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
        createAndStartServer {
            get("/a%20b") {
                call.respondText("space")
            }
            get("/a+b") {
                call.respondText("plus")
            }
        }

        withUrl("/a%20b") {
            assertEquals(200, responseCode)
            assertEquals("space", inputStream.bufferedReader().use { it.readText() })
        }
        withUrl("/a+b") {
            assertEquals(200, responseCode)
            assertEquals("plus", inputStream.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun testFormUrlEncoded() {
        createAndStartServer {
            post("/") {
                call.respondText("${call.parameters["urlp"]},${call.request.receive<ValuesMap>()["formp"]}")
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
            assertEquals("1,2", inputStream.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun testRequestBodyAsyncEcho() {
        createAndStartServer {
            route("/echo") {
                handle {
                    val buffer = ByteBufferWriteChannel()
                    call.request.receive<ReadChannel>().copyTo(buffer)

                    call.respond(object : FinalContent.ReadChannelContent() {
                        override val headers: ValuesMap get() = ValuesMap.Empty
                        override fun readFrom() = buffer.toByteArray().toReadChannel()
                    })
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

            assertEquals(200, responseCode)
            assertEquals("POST test\nAnother line", inputStream.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun testEchoBlocking() {
        createAndStartServer {
            post("/") {
                val text = call.request.receive<ReadChannel>().toInputStream().bufferedReader().readText()
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

            assertEquals(200, responseCode)
            assertEquals("POST content", inputStream.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun testMultipartFileUpload() {
        createAndStartServer {
            post("/") {
                val response = StringBuilder()

                call.request.receive<MultiPartData>().parts.sortedBy { it.partName }.forEach { part ->
                    when (part) {
                        is PartData.FormItem -> response.append("${part.partName}=${part.value}\n")
                        is PartData.FileItem -> response.append("file:${part.partName},${part.originalFileName},${part.streamProvider().bufferedReader().readText()}\n")
                    }

                    part.dispose()
                }

                call.respondText(response.toString())
            }
        }

        withUrl("/") {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            setRequestProperty(HttpHeaders.ContentType, ContentType.MultiPart.FormData.withParameter("boundary", "***bbb***").toString())

            outputStream.bufferedWriter(Charsets.ISO_8859_1).let { out ->
                out.apply {
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

            assertEquals(200, responseCode)
            assertEquals("a story=Hi user. The snake you gave me for free ate all the birds. Please take it back ASAP.\nfile:attachment,original.txt,File content goes here\n", inputStream.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun testRequestTwiceNoKeepAlive() {
        createAndStartServer {
            get("/") {
                call.respond(TextContent("Text", ContentType.Text.Plain))
            }
        }

        withUrl("/") {
            setRequestProperty(HttpHeaders.Connection, "close")
            assertEquals("Text", inputStream.bufferedReader().use { it.readText() })
        }

        withUrl("/") {
            setRequestProperty(HttpHeaders.Connection, "close")
            assertEquals("Text", inputStream.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun testRequestTwiceWithKeepAlive() {
        createAndStartServer {
            get("/") {
                call.respond(TextContent("Text", ContentType.Text.Plain))
            }
        }


        withUrl("/") {
            setRequestProperty(HttpHeaders.Connection, "keep-alive")

            assertEquals(200, responseCode)
            assertEquals("Text", inputStream.bufferedReader().use { it.readText() })
        }

        withUrl("/") {
            setRequestProperty(HttpHeaders.Connection, "keep-alive")

            assertEquals(200, responseCode)
            assertEquals("Text", inputStream.bufferedReader().use { it.readText() })
        }
    }

    @Test
    fun testRequestTwiceInOneBufferWithKeepAlive() {
        createAndStartServer {
            get("/") {
                val d = call.request.queryParameters["d"]!!.toLong()
                delay(d, TimeUnit.SECONDS)

                call.response.header("D", d.toString())
                call.respond(TextContent("Response for $d\n", ContentType.Text.Plain))
            }
        }

        val s = Socket()
        s.tcpNoDelay = true

        val impudent = buildString {
            append("GET /?d=2 HTTP/1.1\r\n")
            append("Host: localhost\r\n")
            append("Connection: keep-alive\r\n")
            append("\r\n")

            append("GET /?d=1 HTTP/1.1\r\n")
            append("Host: localhost\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray()

        s.connect(InetSocketAddress(port))
        s.use {
            s.getOutputStream().apply {
                write(impudent)
                flush()
            }

            val responses = s.getInputStream().bufferedReader(Charsets.ISO_8859_1).lineSequence()
                    .filterNot { it.startsWith("Date") || it.startsWith("Server") || it.startsWith("Content-") || it.toIntOrNull() != null || it.isBlank() || it.startsWith("Connection") }
                    .map { it.trim() }
                    .joinToString(separator = "\n").replace("200 OK", "200")

            assertEquals("""
                HTTP/1.1 200
                D: 2
                Response for 2
                HTTP/1.1 200
                D: 1
                Response for 1
                """.trimIndent().replace("\r\n", "\n"), responses)
        }
    }

    @Test
    fun testRequestContentString() {
        createAndStartServer {
            post("/") {
                call.respond(call.request.receive<String>())
            }
        }

        withUrl("/") {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty(HttpHeaders.ContentType, ContentType.Text.Plain.toString())

            outputStream.use {
                it.write("Hello".toByteArray())
            }

            assertEquals(200, responseCode)
            assertEquals("Hello", inputStream.reader().use { it.readText() })
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
                assertEquals(200, responseCode)
                assertEquals("OK $i", inputStream.reader().use { it.readText() })
            }
        }
    }

    @Test
    fun testRequestContentInputStream() {
        createAndStartServer {
            post("/") {
                call.respond(call.request.receive<InputStream>().reader().readText())
            }
        }

        withUrl("/") {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty(HttpHeaders.ContentType, ContentType.Text.Plain.toString())

            outputStream.use {
                it.write("Hello".toByteArray())
            }

            assertEquals(200, responseCode)
            assertEquals("Hello", inputStream.reader().use { it.readText() })
        }
    }

    @Test
    fun testStatusCodeDirect() {
        createAndStartServer {
            get("/") {
                call.response.status(HttpStatusCode.Found)
                call.respond("Hello")
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.Found.value, responseCode)
            assertEquals("Hello", inputStream.reader().use { it.readText() })
        }
    }

    @Test
    fun testStatusCodeViaResponseObject() {
        var completed = false
        createAndStartServer {
            get("/") {
                call.respond(HttpStatusCode.Found)
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.Found.value, responseCode)
            completed = true
        }
        assertTrue(completed)
    }

    @Test
    fun testStatusCodeViaResponseObject2() {
        createAndStartServer {
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
        createAndStartServer {
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
        createAndStartServer {
            install(XForwardedHeadersSupport)
            get("/") {
                call.respond(call.url { })
            }
        }

        withUrl("/") { port ->
            setRequestProperty(HttpHeaders.XForwardedHost, "my-host:90")

            val expectedProto = if (port == sslPort) "https" else "http"
            assertEquals("$expectedProto://my-host:90/", inputStream.reader().use { it.readText() })
        }

        withUrl("/") { port ->
            setRequestProperty(HttpHeaders.XForwardedHost, "my-host")

            val expectedProto = if (port == sslPort) "https" else "http"
            assertEquals("$expectedProto://my-host/", inputStream.reader().use { it.readText() })
        }

        withUrl("/") {
            setRequestProperty(HttpHeaders.XForwardedHost, "my-host:90")
            setRequestProperty(HttpHeaders.XForwardedProto, "https")

            assertEquals("https://my-host:90/", inputStream.reader().use { it.readText() })
        }

        withUrl("/") {
            setRequestProperty(HttpHeaders.XForwardedHost, "my-host")
            setRequestProperty(HttpHeaders.XForwardedProto, "https")

            assertEquals("https://my-host/", inputStream.reader().use { it.readText() })
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
            assertEquals("/path/1", inputStream.reader().use { it.readText() })
        }
        withUrl("/path/1?") {
            assertEquals("/path/1", inputStream.reader().use { it.readText() })
        }
        withUrl("/path/1") {
            assertEquals("/path/1", inputStream.reader().use { it.readText() })
        }

        withUrl("/document/1?p=v") {
            assertEquals("1", inputStream.reader().use { it.readText() })
        }
        withUrl("/document/1?") {
            assertEquals("1", inputStream.reader().use { it.readText() })
        }
        withUrl("/document/1") {
            assertEquals("1", inputStream.reader().use { it.readText() })
        }

        withUrl("/queryString/1?p=v") {
            assertEquals("p=v", inputStream.reader().use { it.readText() })
        }
        withUrl("/queryString/1?") {
            assertEquals("", inputStream.reader().use { it.readText() })
        }
        withUrl("/queryString/1") {
            assertEquals("", inputStream.reader().use { it.readText() })
        }

        withUrl("/uri/1?p=v") {
            assertEquals("/uri/1?p=v", inputStream.reader().use { it.readText() })
        }
        withUrl("/uri/1?") {
            assertEquals("/uri/1?", inputStream.reader().use { it.readText() })
        }
        withUrl("/uri/1") {
            assertEquals("/uri/1", inputStream.reader().use { it.readText() })
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
            assertEquals("[value]", inputStream.reader().use { it.readText() })
        }
        withUrl("/multiple?multiple=value1&multiple=value2") {
            assertEquals("[value1, value2]", inputStream.reader().use { it.readText() })
        }
        withUrl("/missing") {
            assertEquals("null", inputStream.reader().use { it.readText() })
        }
    }

    @Test
    fun testCallLoggerOnError() {
        val message = "expected, ${nextNonce()}"
        val collected = CopyOnWriteArrayList<Throwable>()

        val log = object : ApplicationLog by SLF4JApplicationLog("embedded") {
            override val name = "DummyLogger"
            override fun fork(name: String) = this
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
        }

        withUrl("/") {
            assertFailsWith<IOException> {
                inputStream.reader().use { it.readText() }
            }

            assertEquals(message, collected.single { it is ExpectedException }.message)
            collected.clear()
        }
    }

    @Test(timeout = 30000L)
    fun testBlockingConcurrency() {
        //println()
        val completed = AtomicInteger(0)
        val executor = Executors.newScheduledThreadPool(3)

        createAndStartServer(executor = executor) {
            get("/{index}") {
                val index = call.parameters["index"]!!.toInt()
                call.respondWrite {
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
                        inputStream.reader().use { reader ->
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

        assertEquals(count * 2, completed.get())
    }

    @Test
    fun testBigFile() {
        val file = File("target/large-file.dat")
        val rnd = Random()

        file.bufferedWriter().use { out ->
            for (line in 1..30000) {
                for (col in 1..(30 + rnd.nextInt(40))) {
                    out.append('a' + rnd.nextInt(25))
                }
                out.append('\n')
            }
        }

        val originalSha1 = file.inputStream().use { it.sha1() }

        createAndStartServer {
            get("/file") { call ->
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/file") {
            assertEquals(originalSha1, inputStream.sha1())
        }
    }

    @Test
    open fun testBlockingDeadlock() {
        createAndStartServer {
            get("/") { call ->
                call.respondWrite(Charsets.ISO_8859_1) {
                    TimeUnit.SECONDS.sleep(1)
                    this.write("Deadlock ?")
                }
            }
        }

        val e = Executors.newCachedThreadPool()
        val q = LinkedBlockingQueue<String>()

        val conns = (0..1000).map { number ->
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
    }

    private fun String.urlPath() = replace("\\", "/")
    private class ExpectedException(message: String) : RuntimeException(message)

    private fun InputStream.sha1(): String {
        val md = MessageDigest.getInstance("SHA1")
        val bytes = ByteArray(8192)

        do {
            val rc = read(bytes)
            if (rc == -1) {
                break
            }
            md.update(bytes, 0, rc)
        } while (true)

        return hex(md.digest())
    }
}