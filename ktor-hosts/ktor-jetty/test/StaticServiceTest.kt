package org.jetbrains.ktor.tests.jetty

import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.routing.*
import org.junit.*
import java.io.*
import java.net.*
import kotlin.concurrent.*
import kotlin.test.*

class StaticServiceTest {
    @Test
    fun testStaticServe() {
        val port = findFreePort()
        val server = embeddedJettyServer(port) {
            route("/files/") {
                serveStatic("org/jetbrains/ktor/tests/jetty")
            }
        }

        thread {
            server.start()
        }
        try {
            URL("http://127.0.0.1:$port/files/${StaticServiceTest::class.simpleName}.class").openStream().buffered().use { it.readBytes() }.let { bytes ->
                assertNotEquals(0, bytes.size)

                // class file signature
                assertEquals(0xca, bytes[0].toInt() and 0xff)
                assertEquals(0xfe, bytes[1].toInt() and 0xff)
                assertEquals(0xba, bytes[2].toInt() and 0xff)
                assertEquals(0xbe, bytes[3].toInt() and 0xff)
            }
            assertFailsWith(FileNotFoundException::class) {
                URL("http://127.0.0.1:$port/files/${StaticServiceTest::class.simpleName}.class2").openStream().buffered().use { it.readBytes() }
            }
            assertFailsWith(FileNotFoundException::class) {
                URL("http://127.0.0.1:$port/wefwefwefw").openStream().buffered().use { it.readBytes() }
            }
        } finally {
            server.stop()
        }
    }

    @Test // for manual testing for now
    @Ignore
    fun testAsync2() {
//        val port = findFreePort()
        val port = 9096
        println("port $port")

        val server = embeddedJettyServer(port) {
            handle {
                response.send(LocalFileContent(File("test/StaticServiceTest.kt")))
            }
        }

        server.start()
    }

    fun findFreePort() = ServerSocket(0).use { it.localPort }
}