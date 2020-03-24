/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import io.ktor.util.*
import kotlin.test.*

internal class URLBuilderTest {
    @Test
    fun testParseSchemeWithDigits() {
        testBuildString("a123://google.com")
    }

    @Test
    fun testParseSchemeWithDotsPlusAndMinusSigns() {
        testBuildString("a.+-://google.com")
    }

    @Test
    fun testParseSchemeWithCapitalCharacters() {
        testBuildString("HTTP://google.com")
    }

    @Test
    fun testParseSchemeNotStartedWithLetter() {
        for (index in 0..0x7F) {
            val char = index.toChar()

            if (char in 'a'..'z' || char in 'A'..'Z') {
                testBuildString("${char}http://google.com")
            } else {
                assertFails("Character $char is not allowed at the first position in the scheme.") {
                    testBuildString("${char}http://google.com")
                }
            }
        }
    }

    @Test
    fun portIsNotInStringIfItMatchesTheProtocolDefaultPort() {
        URLBuilder().apply {
            protocol = URLProtocol("custom", 12345)
            port = 12345
        }.buildString().let {
            assertEquals("custom://localhost/", it)
        }
    }

    @Test
    fun settingTheProtocolDoesNotOverwriteAnExplicitPort() {
        URLBuilder().apply {
            port = 8080
            protocol = URLProtocol.HTTPS
        }.buildString().let { url ->
            assertEquals("https://localhost:8080/", url)
        }
    }

    @Test
    fun protocolDefaultPortIsUsedIfAPortIsNotSpecified() {
        if (PlatformUtils.IS_BROWSER) return

        URLBuilder().apply {
            protocol = URLProtocol.HTTPS

            assertEquals(DEFAULT_PORT, port)
        }.build().also { url ->
            assertEquals(URLProtocol.HTTPS.defaultPort, url.port)
        }
    }

    @Test
    fun anExplicitPortIsUsedIfSpecified() {
        URLBuilder().apply {
            protocol = URLProtocol.HTTPS
            port = 2048

            assertEquals(2048, port)
        }.build().also { url ->
            assertEquals(2048, url.port)
        }
    }

    @Test
    fun takeFromACustomProtocolAndSettingTheDefaultPort() {
        URLBuilder().apply {
            takeFrom("custom://localhost/path")
            protocol = URLProtocol("custom", 8080)

            assertEquals(DEFAULT_PORT, port)
        }.buildString().also { url ->
            // ensure that the built url does not specify the port when configuring the default port
            assertEquals("custom://localhost/path", url)
        }
    }

    @Test
    fun rewritePathWhenRewriteUrl() {
        val url = URLBuilder("https://httpstat.us/301")
        url.takeFrom("https://httpstats.us")

        assertEquals("", url.encodedPath)
    }

    @Test
    fun rewritePathFromSlash() {
        val url = URLBuilder("https://httpstat.us/301")

        url.takeFrom("/")
        assertEquals("https://httpstat.us/", url.buildString())

    }

    @Test
    fun rewritePathFromSingle() {
        val url = URLBuilder("https://httpstat.us/301")

        url.takeFrom("/1")
        assertEquals("https://httpstat.us/1", url.buildString())
    }

    @Test
    fun rewritePathDirectoryWithRelative() {
        val url = URLBuilder("https://example.org/first/directory/")

        url.takeFrom("relative")
        assertEquals("https://example.org/first/directory/relative", url.buildString())
    }

    @Test
    fun rewritePathFileWithRelative() {
        val url = URLBuilder("https://example.org/first/file.html")

        url.takeFrom("relative")
        assertEquals("https://example.org/first/relative", url.buildString())
    }

    @Test
    fun rewritePathFileWithDot() {
        val url = URLBuilder("https://example.org/first/file.html")

        url.takeFrom("./")
        assertEquals("https://example.org/first/./", url.buildString())
    }

    @Test
    fun queryParamsWithNoValue() {
        val url = URLBuilder("https://httpstat.us/?novalue")
        assertEquals("https://httpstat.us/?novalue", url.buildString())
    }

    @Test
    fun queryParamsWithEmptyValue() {
        val url = URLBuilder("https://httpstat.us/?empty=")
        assertEquals("https://httpstat.us/?empty=", url.buildString())
    }

    @Test
    fun emptyProtocolWithPort() {
        val url = URLBuilder("//whatever:8080/abc")

        assertEquals(URLProtocol.HTTP, url.protocol)
        assertEquals("whatever", url.host)
        assertEquals(8080, url.port)
        assertEquals("/abc", url.encodedPath)
    }

    @Test
    fun retainEmptyPath() {
        val url = URLBuilder("http://www.test.com")
        assertEquals("", url.encodedPath)
    }

    /**
     * Checks that the given [url] and the result of [URLBuilder.buildString] is equal (case insensitive).
     */
    private fun testBuildString(url: String) {
        assertEquals(url.toLowerCase(), URLBuilder(url).buildString().toLowerCase())
    }
}
