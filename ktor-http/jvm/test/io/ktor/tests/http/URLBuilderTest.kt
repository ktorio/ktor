/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import java.net.*
import java.util.*
import kotlin.test.*

class URLBuilderTestJvm {
    private val urlString = "http://localhost:8080/path"

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
    fun testEmptyPath() {
        val urlStr1 = "http://localhost"
        val urlStr2 = "http://localhost?param1=foo"
        val uri1 = URI.create(urlStr1)
        val uri2 = URI.create(urlStr2)

        val url1 = URLBuilder().apply {
            takeFrom(uri1)
        }
        val url2 = URLBuilder().apply {
            takeFrom(uri2)
        }

        assertEquals("http://localhost", url1.buildString())
        assertEquals("http://localhost?param1=foo", url2.buildString())
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
            val uri = "custom://localhost:8080/path".replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
            takeFrom(URI.create(uri))
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

    @Test
    fun testUnderscoreInHost() {
        assertEquals(
            "http://my_service:8080",
            URLBuilder().takeFrom(URL("http://my_service:8080")).buildString()
        )
    }
}
