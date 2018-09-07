package io.ktor.tests.http.cio

import io.ktor.http.*
import io.ktor.http.cio.*
import kotlinx.io.streams.*
import org.junit.*
import org.junit.Test
import kotlin.test.*

class RequestResponseBuilderTest {
    private val builder = RequestResponseBuilder()

    @After
    fun tearDown() {
        builder.release()
    }

    @Test
    fun testBuildGet() {
        builder.apply {
            requestLine(HttpMethod.Get, "/", "HTTP/1.1")
            headerLine("Host", "localhost")
            headerLine("Connection", "close")
            emptyLine()
        }

        val packet = builder.build()
        val request = packet.inputStream().reader().readText()

        assertEquals("GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n", request)
    }

    @Test
    fun testBuildOK() {
        builder.apply {
            responseLine("HTTP/1.1", 200, "OK")
            headerLine("Content-Type", "text/plain")
            headerLine("Connection", "close")
            emptyLine()
            line("Hello, World!")
        }

        val packet = builder.build()
        val response = packet.inputStream().reader().readText()

        assertEquals("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\nHello, World!\r\n", response)
    }
}