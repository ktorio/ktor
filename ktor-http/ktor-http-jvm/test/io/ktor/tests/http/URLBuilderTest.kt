package io.ktor.tests.http

import io.ktor.http.*
import java.net.*
import kotlin.test.*


class URLBuilderTest {
    val urlString = "http://localhost:8080/path"

    @Test
    fun takeFromURITest() {
        val url = URLBuilder().apply {
            takeFrom(URI.create(urlString))
        }

        with(url.build()) {
            assertEquals("localhost", host)
            assertEquals("/path", fullPath)
            assertEquals(8080, port)
        }

    }

    @Test
    fun buildStringTest() {
        val url = URLBuilder().apply {
            takeFrom(URI.create(urlString))
        }

        assertEquals(urlString, url.buildString())
    }

    @Test
    fun defaultPortBuildStringTest() {
        val url = URLBuilder().apply {
            takeFrom(URI.create("http://localhost:80/path"))
        }

        assertEquals("http://localhost/path", url.buildString())
    }

    @Test
    fun testCustom() {
        val url = URLBuilder().apply {
            takeFrom(URI.create("custom://localhost:8080/path"))
        }

        assertEquals("custom://localhost:8080/path", url.buildString())
    }

    @Test
    fun testWss() {
        val url = URLBuilder().apply {
            takeFrom(URI.create("wss://localhost/path"))
        }

        assertEquals("wss://localhost/path", url.buildString())
        assertEquals(443, url.port)
    }

    @Test
    fun testCapitalize() {
        val url = URLBuilder().apply {
            takeFrom(URI.create("custom://localhost:8080/path".capitalize()))
        }

        assertEquals("custom://localhost:8080/path", url.buildString())
    }

    @Test
    fun testTakeFromRewritePort() {
        URLBuilder().apply {
            port = 9093
            takeFrom(URI("http://localhost:81/"))
        }.buildString().let { url -> assertEquals("http://localhost:81/", url) }

        URLBuilder().apply {
            port = 9093
            takeFrom(URI("http://localhost/"))
        }.buildString().let { url -> assertEquals("http://localhost/", url) }

        URLBuilder().apply {
            port = 9093
            takeFrom(URI("/test"))
        }.buildString().let { url -> assertEquals("http://localhost:9093/test", url) }
    }
}
