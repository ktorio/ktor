/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

internal class URLParserTest {
    @Test
    fun `encode unicode characters in absolute path`() {
        val url = Url("https://assets.example.org/library/annual–review/cover-image.webp")

        assertEquals("/library/annual%E2%80%93review/cover-image.webp", url.encodedPath)
        assertEquals(listOf("", "library", "annual–review", "cover-image.webp"), url.rawSegments)
        assertEquals(
            "https://assets.example.org/library/annual%E2%80%93review/cover-image.webp",
            url.toString()
        )
    }

    @Test
    fun `preserve percent-encoded characters in path`() {
        val url = Url("https://assets.example.org/library/annual%E2%80%93review/cover-image.webp")

        assertEquals("/library/annual%E2%80%93review/cover-image.webp", url.encodedPath)
        assertEquals(
            "https://assets.example.org/library/annual%E2%80%93review/cover-image.webp",
            url.toString()
        )
    }

    @Test
    fun `encode unicode characters in relative path`() {
        val url = URLBuilder("library/annual–review/cover-image.webp")

        assertEquals("library/annual%E2%80%93review/cover-image.webp", url.encodedPath)
    }

    @Test
    fun `encode only path when URL contains query and fragment`() {
        val url = Url(
            "https://assets.example.org/library/annual–review/cover-image.webp?size=large&download=#preview"
        )

        assertEquals("/library/annual%E2%80%93review/cover-image.webp", url.encodedPath)
        assertEquals("size=large&download=", url.encodedQuery)
        assertEquals("preview", url.encodedFragment)
        assertEquals(
            "https://assets.example.org/library/annual%E2%80%93review/cover-image.webp" +
                "?size=large&download=#preview",
            url.toString()
        )
    }

    @Test
    fun `encode unicode characters in file path`() {
        val urls = listOf(
            "file:/var/reports/annual–review.pdf" to "file:///var/reports/annual%E2%80%93review.pdf",
            "file://localhost/var/reports/annual–review.pdf" to
                "file://localhost/var/reports/annual%E2%80%93review.pdf",
            "file:///var/reports/annual–review.pdf" to "file:///var/reports/annual%E2%80%93review.pdf"
        )

        for ((source, expected) in urls) {
            val url = Url(source)
            assertEquals("/var/reports/annual%E2%80%93review.pdf", url.encodedPath)
            assertEquals(expected, url.toString())
        }
    }
}
