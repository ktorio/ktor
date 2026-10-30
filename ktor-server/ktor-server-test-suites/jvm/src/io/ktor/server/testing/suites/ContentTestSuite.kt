/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.File
import java.io.InputStream
import kotlin.test.*

abstract class ContentTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : ContentCommonTestSuite<TEngine, TConfiguration>(hostFactory) {

    @Test
    fun testStream() = runTest {
        createAndStartServer {
            handle {
                call.respondTextWriter {
                    write("ABC")
                    flush()
                    write("123")
                    flush()
                }
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals("ABC123", bodyAsText())
        }
    }

    @Test
    fun testBinary() = runTest {
        createAndStartServer {
            handle {
                call.respondOutputStream {
                    write(25)
                    write(37)
                    write(42)
                }
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals(ContentType.Application.OctetStream, contentType())
            assertTrue(byteArrayOf(25, 37, 42).contentEquals(readRawBytes()))
        }
    }

    @Test
    fun testLocalFileContent() = runTest {
        val file =
            listOf(File("jvm"), File("ktor-server/ktor-server/jvm"))
                .filter { it.exists() }
                .flatMap { it.walkBottomUp().filter { f -> f.extension == "kt" }.asIterable() }
                .first()

        testLog.trace("test file is {}", file)

        createAndStartServer {
            handle {
                call.respond(LocalFileContent(file))
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals(file.readText(), bodyAsText(Charsets.UTF_8))
        }
    }

    @Test
    fun testLocalFileContentRange() = runTest {
        val file = loadTestFile()
        testLog.trace { "test file is $file" }

        createAndStartServer {
            install(PartialContent)
            handle {
                call.respond(LocalFileContent(file))
            }
        }

        val fileContentHead = String(file.reader().use { input -> CharArray(32).also { input.read(it) } })

        withUrl(
            "/",
            {
                header(
                    HttpHeaders.Range,
                    RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(0, 0))).toString()
                )
            }
        ) {
            assertEquals(HttpStatusCode.PartialContent.value, status.value)
            assertEquals(fileContentHead.take(1), bodyAsText())
        }
        withUrl(
            "/",
            {
                header(
                    HttpHeaders.Range,
                    RangesSpecifier(RangeUnits.Bytes, listOf(ContentRange.Bounded(1, 2))).toString()
                )
            }
        ) {
            assertEquals(HttpStatusCode.PartialContent.value, status.value)
            assertEquals(fileContentHead.substring(1, 3), bodyAsText())
        }
    }

    @Test
    fun testJarFileContent() = runTest {
        createAndStartServer {
            handle {
                call.respond(call.resolveResource("/ArrayList.class", "java.util")!!)
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            readRawBytes().let { bytes ->
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
    fun testURIContent() = runTest {
        createAndStartServer {
            handle {
                call.respond(
                    URIFileContent(
                        this::class.java.classLoader
                            .getResources("java/util/ArrayList.class")
                            .toList()
                            .first()
                    )
                )
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            readRawBytes().let { bytes ->
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
    fun testURIContentLocalFile() = runTest {
        val file =
            File("build/classes/")
                .walkBottomUp()
                .filter { it.extension == "class" }
                .first()
        testLog.trace("test file is {}", file)

        createAndStartServer {
            handle {
                call.respond(URIFileContent(file.toURI()))
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            readRawBytes().let { bytes ->
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
    fun testStreamNoFlush() = runTest {
        createAndStartServer {
            handle {
                call.respondTextWriter {
                    write("ABC")
                    write("123")
                }
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals("ABC123", bodyAsText())
        }
    }

    @Test
    fun testStaticServe() = runTest {
        createAndStartServer {
            staticResources("/files/", "io/ktor/server/testing/suites")
        }

        withUrl("/files/${ContentTestSuite::class.simpleName}.class") {
            assertEquals(200, status.value)
            val bytes = readRawBytes()
            assertNotEquals(0, bytes.size)

            // class file signature
            assertEquals(0xca, bytes[0].toInt() and 0xff)
            assertEquals(0xfe, bytes[1].toInt() and 0xff)
            assertEquals(0xba, bytes[2].toInt() and 0xff)
            assertEquals(0xbe, bytes[3].toInt() and 0xff)
        }
        withUrl("/files/${ContentTestSuite::class.simpleName}.class2") {
            assertEquals(HttpStatusCode.NotFound.value, status.value)
        }
        withUrl("/wefwefwefw") {
            assertEquals(HttpStatusCode.NotFound.value, status.value)
        }
    }

    @Test
    fun testStaticServeFromDir() = runTest {
        val targetClasses = File(classesDir)

        val file =
            targetClasses
                .walkBottomUp()
                .first { it.extension == "class" && !it.name.contains('$') }

        val location = file.parentFile!!

        testLog.trace { "test file is $file" }

        createAndStartServer {
            staticFiles("/files", location)
        }

        withUrl("/files/${file.toRelativeString(location).urlPath()}") {
            assertEquals(200, status.value)

            val bytes = readBytes(100)
            assertNotEquals(0, bytes.size)

            // class file signature
            assertEquals(0xca, bytes[0].toInt() and 0xff)
            assertEquals(0xfe, bytes[1].toInt() and 0xff)
            assertEquals(0xba, bytes[2].toInt() and 0xff)
            assertEquals(0xbe, bytes[3].toInt() and 0xff)
        }

        withUrl("/files/${file.toRelativeString(location).urlPath()}2") {
            assertEquals(404, status.value)
        }
        withUrl("/wefwefwefw") {
            assertEquals(404, status.value)
        }
    }

    @Test
    fun testRequestBodyAsyncEcho() = runTest {
        createAndStartServer {
            route("/echo") {
                handle {
                    val response = call.receiveChannel().toByteArray()
                    call.respond(
                        object : OutgoingContent.ReadChannelContent() {
                            override fun readFrom() = ByteReadChannel(response)
                        }
                    )
                }
            }
        }

        withUrl(
            "/echo",
            {
                method = HttpMethod.Post
                setBody(
                    WriterContent(
                        {
                            append("POST test\n")
                            append("Another line")
                            flush()
                        },
                        ContentType.Text.Plain
                    )
                )
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("POST test\nAnother line", bodyAsText())
        }
    }

    @Test
    fun testEchoBlocking() = runTest {
        createAndStartServer {
            post("/") {
                val text = withContext(Dispatchers.IO) { call.receiveStream().bufferedReader().readText() }
                call.response.status(HttpStatusCode.OK)
                call.respond(text)
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                setBody(ByteArrayContent("POST content".toByteArray()))
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("POST content", bodyAsText())
        }
    }

    @Test
    @Http1Only
    open fun testMultipartFileUpload() = runTest {
        createAndStartServer {
            post("/") {
                val response = StringBuilder()
                call.receiveMultipart().forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> response.append("${part.name}=${part.value}\n")

                        is PartData.FileItem ->
                            response.append(
                                "file:${part.name},${part.originalFileName},${
                                    part.provider().readBuffer().readText()
                                }\n"
                            )

                        is PartData.BinaryItem -> {}

                        is PartData.BinaryChannelItem -> {}
                    }
                }

                call.respondText(response.toString())
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                val contentType =
                    ContentType.MultiPart.FormData
                        .withParameter("boundary", "***bbb***")
                        .withCharset(Charsets.ISO_8859_1)

                setBody(
                    WriterContent(
                        {
                            append("--***bbb***\r\n")
                            append("Content-Disposition: form-data; name=\"a story\"\r\n")
                            append("\r\n")
                            append(
                                "Hi user. The snake you gave me for free ate all the birds. " +
                                    "Please take it back ASAP.\r\n"
                            )
                            append("--***bbb***\r\n")
                            append("Content-Disposition: form-data; name=\"attachment\"; filename=\"original.txt\"\r\n")
                            append("Content-Type: text/plain\r\n")
                            append("\r\n")
                            append("File content goes here\r\n")
                            append("--***bbb***--\r\n")
                            flush()
                        },
                        contentType
                    )
                )
            }
        ) {
            assertEquals(200, status.value)
            assertEquals(
                "a story=Hi user. The snake you gave me for free ate all the birds. " +
                    "Please take it back ASAP.\nfile:attachment,original.txt,File content goes here\n",
                bodyAsText()
            )
        }
    }

    @Test
    @Http1Only
    open fun testMultipartFileUploadLarge() = runTest {
        val numberOfLines = 10000

        createAndStartServer {
            post("/") {
                val response = StringBuilder()
                call.receiveMultipart(formFieldLimit = 1 * 1024 * 1024).forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> response.append("${part.name}=${part.value}\n")

                        is PartData.FileItem -> {
                            val lineSequence = part.provider()
                                .readBuffer()
                                .readText()
                                .lines()

                            response.append("file:${part.name},${part.originalFileName},${lineSequence.count()}\n")
                        }

                        is PartData.BinaryItem -> {
                        }

                        is PartData.BinaryChannelItem -> {}
                    }

                    part.release()
                }
                call.respondText(response.toString())
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                val contentType =
                    ContentType.MultiPart.FormData
                        .withParameter("boundary", "***bbb***")
                        .withCharset(Charsets.ISO_8859_1)

                setBody(
                    WriterContent(
                        {
                            append("--***bbb***\r\n")
                            append("Content-Disposition: form-data; name=\"a story\"\r\n")
                            append("\r\n")
                            append(
                                "Hi user. The snake you gave me for free ate all the birds. " +
                                    "Please take it back ASAP.\r\n"
                            )
                            append("--***bbb***\r\n")
                            append("Content-Disposition: form-data; name=\"attachment\"; filename=\"original.txt\"\r\n")
                            append("Content-Type: text/plain\r\n")
                            append("\r\n")
                            withContext(coroutineContext) {
                                repeat(numberOfLines) {
                                    append("File content goes here\r\n")
                                }
                            }
                            append("--***bbb***--\r\n")
                            flush()
                        },
                        contentType
                    )
                )
            }
        ) {
            assertEquals(200, status.value)
            assertEquals(
                "a story=Hi user. The snake you gave me for free ate all the birds. " +
                    "Please take it back ASAP.\nfile:attachment,original.txt,$numberOfLines\n",
                bodyAsText()
            )
        }
    }

    @Test
    fun testReceiveInputStream() = runTest {
        createAndStartServer {
            post("/") {
                call.respond(withContext(Dispatchers.IO) { call.receive<InputStream>().reader().readText() })
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                setBody("Hello")
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("Hello", bodyAsText())
        }
    }

    @Test
    fun testRequestContentInputStream() = runTest {
        createAndStartServer {
            post("/") {
                call.respond(withContext(Dispatchers.IO) { call.receiveStream().reader().readText() })
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                setBody(ByteArrayContent("Hello".toByteArray(), ContentType.Text.Plain))
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("Hello", bodyAsText())
        }
    }

    @Test
    fun outputStreamIsStreamedToConsumer() = runTest(retries = 3) {
        var readingStarted = false

        createAndStartServer {
            handle {
                call.respondOutputStream {
                    var count = 0
                    while (!readingStarted) {
                        write(++count)
                        assertFalse(count > 10_000_000)
                    }
                }
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals(ContentType.Application.OctetStream, contentType())

            val input: InputStream = body()
            var count = 0
            var byte = 0
            do {
                assertEquals((count++).toByte(), byte.toByte())
                byte = input.read()
                readingStarted = true
            } while (byte >= 0)
        }
    }

    @Test
    fun respondSourceFromInputStream() = runTest {
        val testString = "test".repeat(100)
        createAndStartServer {
            get("/raw") {
                call.respondSource(
                    testString.toByteArray().inputStream().asSource(),
                    contentType = ContentType.Text.Plain
                )
            }
            get("/buffered") {
                call.respondSource(
                    testString.toByteArray().inputStream().asSource().buffered(),
                    contentType = ContentType.Text.Plain
                )
            }
            get("/truncated") {
                call.respondSource(
                    testString.toByteArray().inputStream().asSource(),
                    contentType = ContentType.Text.Plain,
                    contentLength = 20
                )
            }
        }
        withUrl("/raw") {
            assertEquals(testString, bodyAsText(), "Unbuffered input stream content mismatch")
        }
        withUrl("/buffered") {
            assertEquals(testString, bodyAsText(), "Buffered input stream content mismatch")
        }
        withUrl("/truncated") {
            assertEquals(testString.take(20), bodyAsText(), "Buffered input stream content mismatch")
        }
    }

    companion object {
        const val classesDir: String = "build/classes/"
    }
}
