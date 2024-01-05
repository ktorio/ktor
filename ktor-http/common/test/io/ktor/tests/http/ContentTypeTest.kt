/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.http

import io.ktor.http.*
import io.ktor.utils.io.charsets.Charsets
import kotlin.test.*

class ContentTypeTest {

    @Test
    fun contentTypeTextPlain() {
        val ct = ContentType.Text.Plain
        assertEquals("text", ct.contentType)
        assertEquals("plain", ct.contentSubtype)
        assertEquals(0, ct.parameters.size)
    }

    @Test
    fun textPlain() {
        val ct = ContentType.parse("text/plain")
        assertEquals("text", ct.contentType)
        assertEquals("plain", ct.contentSubtype)
        assertEquals(0, ct.parameters.size)
    }

    @Test
    fun testBlankIsAny() {
        assertEquals(ContentType.Any, ContentType.parse(""))
    }

    @Test
    fun textPlainCharsetInQuotes() {
        val ct1 = ContentType.parse("text/plain; charset=us-ascii")
        val ct2 = ContentType.parse("text/plain; charset=\"us-ascii\"")
        assertEquals(ct1, ct2)
    }

    @Test
    fun textPlainCharsetCaseInsensitive() {
        val ct1 = ContentType.parse("Text/plain; charset=UTF-8")
        val ct2 = ContentType.parse("text/Plain; CHARSET=utf-8")
        assertEquals(ct1, ct2)
    }

    @Test
    fun textPlainCharsetIsUtf8() {
        val ct = ContentType.parse("text/plain ; charset = utf-8")
        assertEquals("text", ct.contentType)
        assertEquals("plain", ct.contentSubtype)
        assertEquals(1, ct.parameters.size)
        assertEquals(HeaderValueParam("charset", "utf-8"), ct.parameters[0])
        val toString = ct.toString()
        assertEquals("text/plain; charset=utf-8", toString)
        assertEquals(ContentType.Text.Plain.withParameter("charset", "utf-8"), ct)
    }

    @Test
    fun textPlainCharsetIsUtf8WithParameterFooBar() {
        val ct = ContentType.parse("text/plain ; charset = utf-8;foo=bar")

        val toString = ct.toString()
        assertEquals("text/plain; charset=utf-8; foo=bar", toString)
    }

    @Test
    fun textPlainInvalid() {
        assertFailsWith(BadContentTypeFormatException::class) {
            ContentType.parse("text/plain/something")
        }
    }

    @Test
    fun testContentSubtypeWithSpace() {
        assertFailsWith<BadContentTypeFormatException> {
            ContentType.parse("text/html xxx")
        }
    }

    @Test
    fun testContentTypeWithSpace() {
        assertFailsWith<BadContentTypeFormatException> {
            ContentType.parse("text xxx/html")
        }
    }

    @Test
    fun contentTypeWithEmptyParametersBlock() {
        assertEquals(ContentType.Text.Plain, ContentType.parse("text/plain; "))
        assertEquals(ContentType.Text.Plain, ContentType.parse("text/plain;"))
    }

    @Test
    fun contentTypeRenderWorks() {
        // rendering tests are in [HeadersTest] so it is just a smoke test
        assertEquals("text/plain; p1=v1", ContentType.Text.Plain.withParameter("p1", "v1").toString())
    }

    @Test
    fun testContentTypeInvalid() {
        val result = ContentType.parse("image/png; charset=utf-8\" but not really")
        assertEquals(ContentType.Image.PNG.withParameter("charset", "utf-8\" but not really"), result)
    }

    @Test
    fun testContentTypeSingleQuoteAtStart() {
        val result = ContentType.parse("image/png; charset=\"utf-8 but not really")
        assertEquals(ContentType.Image.PNG.withParameter("charset", "\"utf-8 but not really"), result)
    }

    @Test
    fun testContentTypeQuotedAtStartAndMiddle() {
        val result = ContentType.parse("image/png; charset=\"utf-8\" but not really")
        assertEquals(ContentType.Image.PNG.withParameter("charset", "\"utf-8\" but not really"), result)
    }

    @Test
    fun testWithoutParameters() {
        assertEquals(ContentType.Text.Plain, ContentType.Text.Plain.withoutParameters())
        assertEquals(
            ContentType.Text.Plain,
            ContentType.Text.Plain.withParameter("a", "1").withoutParameters()
        )

        assertEquals(
            "text/plain",
            ContentType.Text.Plain.withParameter("a", "1").withoutParameters().toString()
        )

        assertEquals(
            "text/html",
            ContentType.parse("text/html;charset=utf-8").withoutParameters().toString()
        )
    }

    @Test
    fun testOnlyLastContentTypeIsProcessed() {
        val contentType = "text/plain; charset=UTF-8, text/html; charset=UTF-8"
        val content = ContentType.parse(contentType)
        assertEquals("text/html; charset=UTF-8", content.toString())
    }

    @Test
    fun testNoCharsetForNonText() {
        assertNull(ContentType.Audio.MP4.withCharsetIfNeeded(Charsets.UTF_8).charset())
        assertNull(ContentType.Application.Json.withCharsetIfNeeded(Charsets.UTF_8).charset())
        assertNull(ContentType("application", "json-patch+json").withCharsetIfNeeded(Charsets.UTF_8).charset())
    }

    @Test
    fun testCharsetForText() {
        assertEquals(Charsets.UTF_8, ContentType.Text.Any.withCharsetIfNeeded(Charsets.UTF_8).charset())
        assertEquals(Charsets.UTF_8, ContentType.Text.Html.withCharsetIfNeeded(Charsets.UTF_8).charset())
        assertEquals(Charsets.UTF_8, ContentType("Text", "custom").withCharsetIfNeeded(Charsets.UTF_8).charset())
    }
}
