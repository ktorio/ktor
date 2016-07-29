package org.jetbrains.ktor.tests.auth

import org.jetbrains.ktor.client.*
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.http.toHttpDateString
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.time.LocalDateTime
import java.util.concurrent.ArrayBlockingQueue
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class HttpClientTest {
    @Test
    fun testDefaultHttpConnection() {
        val portSync = ArrayBlockingQueue<Int>(1)
        val headersSync = ArrayBlockingQueue<Map<String, String>>(1)
        val receivedContentSync = ArrayBlockingQueue<String>(1)

        val th = thread {
            ServerSocket(0, -50, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))).use { server ->
                portSync.add(server.localPort)

                server.accept()!!.use { client ->
                    val reader = client.inputStream.bufferedReader()

                    val headers = reader.lineSequence().takeWhile { it.isNotBlank() }
                            .associateBy({ it.substringBefore(":", "") }, { it.substringAfter(":").trimStart() })
                    headersSync.add(headers)

                    val requestContentBuffer = CharArray(headers[HttpHeaders.ContentLength]!!.toInt())
                    var read = 0
                    while (read < requestContentBuffer.size) {
                        val rc = reader.read(requestContentBuffer, read, requestContentBuffer.size - read)
                        require(rc != -1) { "premature end of stream" }

                        read += rc
                    }

                    val requestContent = String(requestContentBuffer)
                    receivedContentSync.add(requestContent)

                    client.outputStream.writer().apply {
                        write("""
                    HTTP/1.1 200 OK
                    Server: test
                    Date: ${LocalDateTime.now().toHttpDateString()}
                    Connection: close
                    """.trimIndent().lines().joinToString("\r\n", postfix = "\r\n\r\nok"))
                        flush()
                    }
                }
            }
        }

        val port = portSync.take()
        val response = DefaultHttpClient.openBlocking(URL("http://127.0.0.1:$port/")) {
            method = HttpMethod.Post
            path = "/url"
            header("header", "value")
            body = { out ->
                out.writer().use { w ->
                    w.write("request-body")
                }
            }
        }

        try {
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("test", response.headers[HttpHeaders.Server])
            assertEquals("ok", response.stream.reader().readText())

            val receivedHeaders = headersSync.take()
            assertEquals("value", receivedHeaders["header"])
            assertEquals("POST /url HTTP/1.1", receivedHeaders[""])
            assertEquals("127.0.0.1:$port", receivedHeaders[HttpHeaders.Host])

            assertEquals("request-body", receivedContentSync.take())
        } finally {
            response.close()
            th.join()
        }
    }
}