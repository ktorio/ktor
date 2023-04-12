/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class DataUrlTest {

    @Test
    fun parsingDataUrl() {
        DataUrl("data:,").let {
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.US_ASCII), it.contentType)
            assertContentEquals(ByteArray(0), it.data)
        }

        assertContentEquals(
            "Hello, World!".toByteArray(Charsets.US_ASCII),
            DataUrl("data:,Hello%2C%20World%21").data
        )

        assertContentEquals(
            "Hello, World!".toByteArray(Charsets.US_ASCII),
            DataUrl("data:;base64,SGVsbG8sIFdvcmxkIQ==").data
        )

        DataUrl("data:text/plain;base64,SGVsbG8sIFdvcmxkIQ==").let {
            assertEquals(ContentType.Text.Plain, it.contentType)
            assertContentEquals("Hello, World!".toByteArray(Charsets.US_ASCII), it.data)
        }

        DataUrl("data:text/plain;charset=UTF-8,%CF%94").let {
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), it.contentType)
            assertContentEquals("ϔ".toByteArray(Charsets.UTF_8), it.data)
        }

        DataUrl("data:text/html,%3Ch1%3EHello%2C%20World%21%3C%2Fh1%3E").let {
            assertEquals(ContentType.Text.Html, it.contentType)
            assertContentEquals("<h1>Hello, World!</h1>".toByteArray(Charsets.UTF_8), it.data)
        }

        DataUrl("data:text/html;charset=UTF-8;param=value,data").let {
            assertEquals(
                ContentType.Text.Html.withCharset(Charsets.UTF_8).withParameter("param", "value"),
                it.contentType
            )
            assertContentEquals("data".toByteArray(Charsets.UTF_8), it.data)
        }

        DataUrl("data:text/plain;charset=UTF-8;param=value;base64,ZGF0YQ==").let {
            assertEquals(
                ContentType.Text.Plain.withCharset(Charsets.UTF_8).withParameter("param", "value"),
                it.contentType
            )
            assertContentEquals("data".toByteArray(Charsets.UTF_8), it.data)
        }

        val svg = """
            <?xml version="1.0" encoding="UTF-8"?><svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"/>
        """.trimIndent()
        DataUrl("data:image/svg+xml,${svg.encodeURLPath(encodeSlash = false)}").let {
            assertEquals(ContentType.Image.SVG, it.contentType)
            assertContentEquals(svg.toByteArray(Charsets.UTF_8), it.data)
        }

        assertFailsWith<URLParserException> {
            DataUrl("invalid")
        }.let {
            assertEquals("Expect data protocol", it.cause?.message)
        }

        assertFailsWith<URLParserException> {
            DataUrl("http://localhost")
        }.let {
            assertEquals("Expect data protocol", it.cause?.message)
        }

        assertFailsWith<URLParserException> {
            DataUrl("data:")
        }.let {
            assertEquals("Expect , or ; at position 5", it.cause?.message)
        }

        assertFailsWith<URLParserException> {
            DataUrl("data:media-type,")
        }.let {
            assertEquals("Bad Content-Type format: media-type", it.cause?.message)
        }

        assertFailsWith<URLParserException> {
            DataUrl("data:;base42,")
        }.let {
            assertEquals("Expect ';base64' string at position 5", it.cause?.message)
        }

        assertFailsWith<URLParserException> {
            DataUrl("data:;base64")
        }.let {
            assertEquals("Expect , at position 12", it.cause?.message)
        }
    }

    @Test
    fun stringifyDataUrl() {
        listOf(
            "data:,",
            "data:,Hello%2C%20World%21",
            "data:;base64,SGVsbG8sIFdvcmxkIQ==",
            "data:text/plain;base64,SGVsbG8sIFdvcmxkIQ==",
            "data:text/plain;charset=UTF-8,%CF%94",
            "data:text/html,%3Ch1%3EHello%2C%20World%21%3C%2Fh1%3E",
            "data:text/html;charset=UTF-8;param=value,data",
            "data:text/plain;charset=UTF-8;param=value;base64,ZGF0YQ==",
            (
                "data:image/svg+xml,%3C%3Fxml%20version%3D%221.0%22%20encoding%3D%22UTF-8%22%3F%3E%3Csvg%20" +
                    "xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2F" +
                    "svg%22%20width%3D%221%22%20height%3D%221%22%2F%3E"
                ).trimIndent().encodeURLPath(encodeSlash = false)
        ).forEach {
            assertEquals(it, DataUrl(it).toString())
        }
    }
}
