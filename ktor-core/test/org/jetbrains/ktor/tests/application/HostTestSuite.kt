package org.jetbrains.ktor.tests.application

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
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
                response.send(TextContent(ContentType.Text.Plain, "test"))
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
                response.stream {
                    writer().apply {
                        write("ABC")
                        flush()
                        write("123")
                        flush()
                    }
                }
                ApplicationCallResult.Handled
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
                response.stream {
                    write("ABC".toByteArray())
                    write("123".toByteArray())
                }
                ApplicationCallResult.Handled
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
                response.sendText(ContentType.Text.Plain, "Hello")
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
                response.sendRedirect("http://localhost:$port/page", true)
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
                response.headers.append(HttpHeaders.ETag, "test-etag")
                response.sendText(ContentType.Text.Plain, "Hello")
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
                response.cookies.append("k1", "v1")
                response.sendText(ContentType.Text.Plain, "Hello")
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
                response.send(LocalFileContent(file))
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
            handle {
                response.send(LocalFileContent(file))
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
            handle {
                response.send(LocalFileContent(file))
            }
        }
        startServer(server)

        withUrl("/") {
            setRequestProperty(HttpHeaders.Range, PartialContentRange(RangeUnits.Bytes, listOf(ContentRange.ClosedContentRange(0, 0))).toString())
            assertEquals("p", inputStream.reader().readText())
        }
        withUrl("/") {
            setRequestProperty(HttpHeaders.Range, PartialContentRange(RangeUnits.Bytes, listOf(ContentRange.ClosedContentRange(1, 2))).toString())
            assertEquals("ac", inputStream.reader().readText())
        }
    }

    @Test
    fun testLocalFileContentRangeWithCompression() {
        val file = listOf(File("src"), File("ktor-core/src")).first { it.exists() }.walkBottomUp().filter { it.extension == "kt" && it.reader().use { it.read().toChar() == 'p' } }.first()
        println("test file is $file")

        val server = createServer(port) {
            handle {
                response.send(LocalFileContent(file))
            }
        }
        startServer(server)

        withUrl("/") {
            addRequestProperty(HttpHeaders.AcceptEncoding, "gzip")
            setRequestProperty(HttpHeaders.Range, PartialContentRange(RangeUnits.Bytes, listOf(ContentRange.ClosedContentRange(0, 0))).toString())

            assertEquals("p", inputStream.reader().readText()) // it should be no compression if range requested
        }
    }

    @Test
    fun testJarFileContent() {
        val server = createServer(port) {
            handle {
                response.send(resolveClasspathWithPath("java/util", "/ArrayList.class")!!)
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
                response.send(URIFileContent(javaClass.classLoader.getResources("java/util/ArrayList.class").toList().first()))
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
                response.send(URIFileContent(file.toURI()))
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

    fun findFreePort() = ServerSocket(0).use { it.localPort }
    fun withUrl(path: String, block: HttpURLConnection.() -> Unit) {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = false

        connection.block()
    }
}