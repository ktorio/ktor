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
    fun testCapitalize() {
        val url = URLBuilder().apply {
            takeFrom(URI.create("custom://localhost:8080/path".capitalize()))
        }

        assertEquals("custom://localhost:8080/path", url.buildString())
    }
}