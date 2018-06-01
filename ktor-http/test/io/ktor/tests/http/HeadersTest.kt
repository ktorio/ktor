package io.ktor.tests.http

import io.ktor.http.*
import org.junit.Test
import kotlin.test.*

class HeadersTest {
    @Test
    fun `parse simple accept header`() {
        val items = parseAndSortContentTypeHeader("audio/basic")
        assertEquals(1, items.count())
        assertEquals("audio/basic", items.single().value)
    }

    @Test
    fun `parse accept header with fallback`() {
        val items = parseAndSortContentTypeHeader("audio/*; q=0.2, audio/basic")
        assertEquals(2, items.count())
        assertEquals("audio/basic", items[0].value)
        assertEquals("audio/*", items[1].value)
    }

    @Test
    fun `parse accept header with preference`() {
        val items = parseAndSortContentTypeHeader("text/plain; q=0.5, text/html,text/x-dvi; q=0.8, text/x-c")
        assertEquals(4, items.count())
        assertEquals(HeaderValue("text/html"), items[0])
        assertEquals(HeaderValue("text/x-c"), items[1])
        assertEquals(HeaderValue("text/x-dvi", listOf(HeaderValueParam("q", "0.8"))), items[2])
        assertEquals(HeaderValue("text/plain", listOf(HeaderValueParam("q", "0.5"))), items[3])
    }

    @Test
    fun `parse accept header with extra parameters`() {
        val items = parseAndSortContentTypeHeader("text/*, text/html, text/html;level=1, */*")
        val item0 = HeaderValue("text/html", listOf(HeaderValueParam("level", "1")))
        val item1 = HeaderValue("text/html")
        val item2 = HeaderValue("text/*")
        val item3 = HeaderValue("*/*")
        assertEquals(listOf(item0, item1, item2, item3), items)
    }

    @Test
    fun `parse accept header with extra parameters and fallback`() {
        val items = parseAndSortContentTypeHeader("text/*;q=0.3, text/html;q=0.7, text/html;level=1,text/html;level=2;q=0.4, */*;q=0.5")
        val item0 = HeaderValue("text/html", listOf(HeaderValueParam("level", "1")))
        val item1 = HeaderValue("text/html", listOf(HeaderValueParam("q", "0.7")))
        val item2 = HeaderValue("*/*", listOf(HeaderValueParam("q", "0.5")))
        val item3 = HeaderValue("text/html", listOf(HeaderValueParam("level", "2"), HeaderValueParam("q", "0.4")))
        val item4 = HeaderValue("text/*", listOf(HeaderValueParam("q", "0.3")))
        assertEquals(item0, items[0])
        assertEquals(item1, items[1])
        assertEquals(item2, items[2])
        assertEquals(item3, items[3])
        assertEquals(item4, items[4])
        assertEquals(5, items.count())
    }

    @Test
    fun `parse single value`() {
        val headerValue = parseHeaderValue("justValue")
        assertEquals(listOf(HeaderValue("justValue")), headerValue)
    }

    @Test
    fun `parse just value with single parameter with value`() {
        val headerValue = parseHeaderValue("justValue;a=b")
        assertEquals(listOf(HeaderValue("justValue", listOf(HeaderValueParam("a", "b")))), headerValue
        )
    }

    @Test
    fun `parse just value with single parameter`() {
        val headerValue = parseHeaderValue("justValue;implicit")
        assertEquals(listOf(HeaderValue("justValue", listOf(HeaderValueParam("implicit", "")))), headerValue
        )
    }

    @Test
    fun `parse just value with single parameter and spaces`() {
        val expected = listOf(HeaderValue("justValue", listOf(HeaderValueParam("a", "b"))))
        assertEquals(expected, parseHeaderValue("justValue; a=b"))
        assertEquals(expected, parseHeaderValue("justValue ; a=b"))
        assertEquals(expected, parseHeaderValue("justValue ; a= b"))
        assertEquals(expected, parseHeaderValue("justValue ; a = b"))
    }

    @Test
    fun `parse just value with multiple parameters`() {
        val headerValue = parseHeaderValue("justValue; a=b; c=d")
        assertEquals(listOf(HeaderValue("justValue", listOf(
                HeaderValueParam("a", "b"),
                HeaderValueParam("c", "d")
        ))),
                headerValue)
    }

    @Test
    fun `parse just value with quoted parameter`() {
        assertEquals(
                listOf(HeaderValue("justValue", listOf(
                        HeaderValueParam("a", "quoted;=,\"value")
                ))),
                parseHeaderValue("justValue; a=\"quoted;=,\\\"value\"")
        )
    }

    @Test
    fun `parse just value with quoted and simple parameters`() {
        assertEquals(
                listOf(HeaderValue("justValue", listOf(
                        HeaderValueParam("a", "quoted;=,\"value"),
                        HeaderValueParam("b", "3"),
                        HeaderValueParam("q", "0.1")
                ))),
                parseHeaderValue("justValue; a=\"quoted;=,\\\"value\"; b=3; q=0.1")
        )
    }

    @Test
    fun `parse broken headers shouldnt fail`() {
        assertEquals(listOf(HeaderValue("justValue")), parseHeaderValue("justValue;"))
        assertEquals(listOf(HeaderValue("justValue")), parseHeaderValue("justValue;;"))
        assertEquals(listOf(HeaderValue("justValue")), parseHeaderValue("justValue;="))
        assertEquals(listOf(HeaderValue("a=b")), parseHeaderValue("a=b"))
        assertEquals(listOf(HeaderValue("", listOf(HeaderValueParam("a", "b")))), parseHeaderValue(";a=b"))
        assertEquals(listOf(HeaderValue("justValue")), parseHeaderValue("justValue;=;"))
        assertEquals(listOf(HeaderValue("justValue")), parseHeaderValue("justValue;=33"))
        assertEquals(listOf(HeaderValue("justValue")), parseHeaderValue("justValue;====33"))
        assertEquals(listOf(HeaderValue("justValue")), parseHeaderValue("justValue;=\""))
        assertEquals(listOf(HeaderValue("justValue", listOf(HeaderValueParam("x", "")))), parseHeaderValue("justValue;x=\"\""))
        assertEquals(listOf(HeaderValue("justValue", listOf(HeaderValueParam("x", "abc\\")))), parseHeaderValue("justValue;x=\"abc\\"))
    }

    @Test
    fun `parse real life headers shouldnt fail`() {
        val examples = listOf(
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "ru,en-US;q=0.7,en;q=0.3",
                "gzip, deflate",
                """If-Match: "strong", W/"weak", "oops, a \"comma\""""",
                """WWW-Authenticate: Newauth realm="newauth";test="oh, a \"comma\""; foo=a'b'c, Basic realm="basic"""",
                "remixlang=0; remixflash=11.2.202; remixscreen_depth=24; remixdt=0; audio_vol=35; remixrefkey=836214a50b5b18f112; audio_time_left=0; remixtst=483196cd; remixsid=63476f202634a7b7f6e9975e8b446b126c1d9c82a94e38801bcc3; remixsslsid=1"
        )

        examples.forEach {
            parseHeaderValue(it)
        }
    }

    @Test
    fun `parse parameters only`() {
        assertEquals(listOf(HeaderValue("", listOf(HeaderValueParam("k", "v")))), parseHeaderValue("k=v", parametersOnly = true))
        assertEquals(listOf(HeaderValue("", listOf(HeaderValueParam("k", "v"), HeaderValueParam("k2", "v2")))), parseHeaderValue("k=v;k2=v2", parametersOnly = true))
        assertEquals(listOf(HeaderValue("", listOf(HeaderValueParam("k", "v"))),
                HeaderValue("", listOf(HeaderValueParam("k2", "v2")))), parseHeaderValue("k=v,k2=v2", parametersOnly = true))
    }

    @Test
    fun testRenderSimple() {
        assertEquals("file", ContentDisposition.File.toString())
    }

    @Test
    fun testRenderSimpleWithParameter() {
        assertEquals("file; k=v", ContentDisposition.File.withParameter("k", "v").toString())
    }

    @Test
    fun testRenderSimpleWithMultipleParameters() {
        assertEquals("file; k1=v1; k2=v2", ContentDisposition.File.withParameters(listOf(
                HeaderValueParam("k1", "v1"),
                HeaderValueParam("k2", "v2")
        )).toString())
    }

    @Test
    fun testRenderEscaped() {
        assertEquals("file; k=\"v,v\"", ContentDisposition.File.withParameter("k", "v,v").toString())
        assertEquals("file; k=\"v,v\"; k2=\"=\"", ContentDisposition.File.withParameter("k", "v,v").withParameter("k2", "=").toString())
        assertEquals("file; k=\"v,v\"; k2=v2", ContentDisposition.File.withParameter("k", "v,v").withParameter("k2", "v2").toString())
    }

    @Test
    fun `headersOf should be case insensitive`() {
        val value = "world"
        assertEquals(value, headersOf("hello", value)["HELLO"])
        assertEquals(value, headersOf("hello", listOf(value))["HELLO"])
        assertEquals(value, headersOf("hello" to listOf(value))["HELLO"])
    }
}