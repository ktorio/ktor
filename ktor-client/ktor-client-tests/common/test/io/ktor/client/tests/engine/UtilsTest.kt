/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.engine

import io.ktor.client.engine.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlin.test.*

@OptIn(InternalAPI::class)
class UtilsTest {
    @Test
    fun testMergeHeaders() {
        val headers = HeadersBuilder().apply {
            append("Accept", "application/xml")
            append("Accept", "application/json")
        }

        val result = mutableMapOf<String, String>()
        mergeHeaders(headers.build(), EmptyContent) { key, value ->
            result[key] = value
        }

        assertEquals("application/xml,application/json", result["Accept"])
    }

    @Test
    fun testDateHeadersAreNotMerged() {
        val date = GMTDate().toHttpDate()
        val headers = HeadersBuilder().apply {
            append(HttpHeaders.Date, date)
            append(HttpHeaders.Date, date)
            append(HttpHeaders.LastModified, date)
            append(HttpHeaders.LastModified, date)
        }

        val result = buildHeaders {
            mergeHeaders(headers.build(), EmptyContent) { key, value ->
                append(key, value)
            }
        }

        val dateHeader = result.getAll(HttpHeaders.Date) ?: fail()
        assertEquals(2, dateHeader.size)

        dateHeader.forEach { assertEquals(date, it) }

        val lastModifiedHeader = result.getAll(HttpHeaders.LastModified) ?: fail()
        assertEquals(2, lastModifiedHeader.size)

        lastModifiedHeader.forEach { assertEquals(date, it) }
    }

    @Test
    fun `lowercase content headers are passed once with canonical names`() {
        val headers = HeadersBuilder().apply {
            append("content-type", "application/json")
            append("content-length", "5")
        }

        val result = mutableListOf<Pair<String, String>>()
        mergeHeaders(headers.build(), object : OutgoingContent.NoContent() {}) { key, value ->
            result += key to value
        }

        assertEquals(
            listOf(HttpHeaders.ContentType to "application/json", HttpHeaders.ContentLength to "5"),
            result.filter { (key, _) -> key.startsWith("content-", ignoreCase = true) }
        )
    }

    @Test
    fun `lowercase date headers are not merged`() {
        val date = GMTDate().toHttpDate()
        val headers = HeadersBuilder().apply {
            append("date", date)
            append("date", date)
            append("if-modified-since", date)
            append("if-modified-since", date)
        }

        val result = buildHeaders {
            mergeHeaders(headers.build(), EmptyContent) { key, value ->
                append(key, value)
            }
        }

        assertEquals(listOf(date, date), result.getAll(HttpHeaders.Date))
        assertEquals(listOf(date, date), result.getAll(HttpHeaders.IfModifiedSince))
    }

    @Test
    fun `lowercase cookie headers are merged with semicolon`() {
        val headers = HeadersBuilder().apply {
            append("cookie", "a=1")
            append("cookie", "b=2")
        }

        val result = mutableMapOf<String, String>()
        mergeHeaders(headers.build(), EmptyContent) { key, value ->
            result[key] = value
        }

        assertEquals("a=1; b=2", result["cookie"])
    }
}
