package io.ktor.tests.http

import io.ktor.http.*
import org.junit.Test
import kotlin.test.*

class ContentTypeTest {

    @Test
    fun `ContentType text-plain`() {
        val ct = ContentType.Text.Plain
        assertEquals("text", ct.contentType)
        assertEquals("plain", ct.contentSubtype)
        assertEquals(0, ct.parameters.size)
    }

    @Test
    fun `text-plain`() {
        val ct = ContentType.parse("text/plain")
        assertEquals("text", ct.contentType)
        assertEquals("plain", ct.contentSubtype)
        assertEquals(0, ct.parameters.size)
    }

    @Test
    fun `text-plain charset in quotes`() {
        val ct1 = ContentType.parse("text/plain; charset=us-ascii")
        val ct2 = ContentType.parse("text/plain; charset=\"us-ascii\"")
        assertEquals(ct1, ct2)
    }

    @Test
    fun `text-plain charset case insensitive`() {
        val ct1 = ContentType.parse("Text/plain; charset=UTF-8")
        val ct2 = ContentType.parse("text/Plain; CHARSET=utf-8")
        assertEquals(ct1, ct2)
    }

    @Test
    fun `text-plain charset is utf-8`() {
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
    fun `text-plain charset is utf-8 with parameter foo-bar`() {
        val ct = ContentType.parse("text/plain ; charset = utf-8;foo=bar")

        val toString = ct.toString()
        assertEquals("text/plain; charset=utf-8; foo=bar", toString)
    }

    @Test
    fun `text-plain-invalid`() {
        assertFailsWith(BadContentTypeFormatException::class) {
            ContentType.parse("text/plain/something")
        }
    }

    @Test
    fun `content type with empty parameters block`() {
        assertEquals(ContentType.Text.Plain, ContentType.parse("text/plain; "))
        assertEquals(ContentType.Text.Plain, ContentType.parse("text/plain;"))
    }

    @Test
    fun `content type render works`() {
        // rendering tests are in [HeadersTest] so it is just a smoke test
        assertEquals("text/plain; p1=v1", ContentType.Text.Plain.withParameter("p1", "v1").toString())
    }
}
