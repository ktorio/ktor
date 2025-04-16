/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class CommonHeadersTest {
    @Test
    fun parseSimpleAcceptHeader() {
        val items = parseAndSortContentTypeHeader("audio/basic")
        assertEquals(1, items.count())
        assertEquals("audio/basic", items.single().value)
    }

    @Test
    fun parseAcceptHeaderWithFallback() {
        val items = parseAndSortContentTypeHeader("audio/*; q=0.2, audio/basic")
        assertEquals(2, items.count())
        assertEquals("audio/basic", items[0].value)
        assertEquals("audio/*", items[1].value)
    }

    @Test
    fun parseAcceptHeaderWithPreference() {
        val items = parseAndSortContentTypeHeader("text/plain; q=0.5, text/html,text/x-dvi; q=0.8, text/x-c")
        assertEquals(4, items.count())
        assertEquals(HeaderValue("text/html"), items[0])
        assertEquals(HeaderValue("text/x-c"), items[1])
        assertEquals(HeaderValue("text/x-dvi", listOf(HeaderValueParam("q", "0.8"))), items[2])
        assertEquals(HeaderValue("text/plain", listOf(HeaderValueParam("q", "0.5"))), items[3])
    }

    @Test
    fun parseAcceptHeaderWithExtraParameters() {
        val items = parseAndSortContentTypeHeader("text/*, text/html, text/html;level=1, */*")
        val item0 = HeaderValue("text/html", listOf(HeaderValueParam("level", "1")))
        val item1 = HeaderValue("text/html")
        val item2 = HeaderValue("text/*")
        val item3 = HeaderValue("*/*")
        assertEquals(listOf(item0, item1, item2, item3), items)
    }

    @Test
    fun parseAcceptHeaderWithExtraParametersAndFallback() {
        val items = parseAndSortContentTypeHeader(
            "text/*;q=0.3, text/html;q=0.7, text/html;level=1,text/html;level=2;q=0.4, */*;q=0.5"
        )
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
    fun parseSingleValue() {
        val headerValue = parseHeaderValue("justValue")
        assertEquals(listOf(HeaderValue("justValue")), headerValue)
    }

    @Test
    fun parseJustValueWithSingleParameterWithValue() {
        val headerValue = parseHeaderValue("justValue;a=b")
        assertEquals(
            listOf(HeaderValue("justValue", listOf(HeaderValueParam("a", "b")))),
            headerValue
        )
    }

    @Test
    fun parseJustValueWithSingleParameter() {
        val headerValue = parseHeaderValue("justValue;implicit")
        assertEquals(
            listOf(HeaderValue("justValue", listOf(HeaderValueParam("implicit", "")))),
            headerValue
        )
    }

    @Test
    fun parseJustValueWithSingleParameterAndSpaces() {
        val expected = listOf(HeaderValue("justValue", listOf(HeaderValueParam("a", "b"))))
        assertEquals(expected, parseHeaderValue("justValue; a=b"))
        assertEquals(expected, parseHeaderValue("justValue ; a=b"))
        assertEquals(expected, parseHeaderValue("justValue ; a= b"))
        assertEquals(expected, parseHeaderValue("justValue ; a = b"))
    }

    @Test
    fun parseJustValueWithMultipleParameters() {
        val headerValue = parseHeaderValue("justValue; a=b; c=d")
        assertEquals(
            listOf(
                HeaderValue(
                    "justValue",
                    listOf(
                        HeaderValueParam("a", "b"),
                        HeaderValueParam("c", "d")
                    )
                )
            ),
            headerValue
        )
    }

    @Test
    fun parseJustValueWithQuotedParameter() {
        assertEquals(
            listOf(
                HeaderValue(
                    "justValue",
                    listOf(HeaderValueParam("a", "quoted;=,\"value"))
                )
            ),
            parseHeaderValue("justValue; a=\"quoted;=,\\\"value\"")
        )
    }

    @Test
    fun parseJustValueWithQuotedAndSimpleParameters() {
        assertEquals(
            listOf(
                HeaderValue(
                    "justValue",
                    listOf(
                        HeaderValueParam("a", "quoted;=,\"value"),
                        HeaderValueParam("b", "3"),
                        HeaderValueParam("q", "0.1")
                    )
                )
            ),
            parseHeaderValue("justValue; a=\"quoted;=,\\\"value\"; b=3; q=0.1")
        )
    }

    @Test
    fun parseBrokenHeadersShouldntFail() {
        assertEquals(listOf(HeaderValue("justValue")), parseHeaderValue("justValue;"))
        assertEquals(listOf(HeaderValue("justValue")), parseHeaderValue("justValue;;"))
        assertEquals(listOf(HeaderValue("justValue")), parseHeaderValue("justValue;="))
        assertEquals(listOf(HeaderValue("a=b")), parseHeaderValue("a=b"))
        assertEquals(listOf(HeaderValue("", listOf(HeaderValueParam("a", "b")))), parseHeaderValue(";a=b"))
        assertEquals(listOf(HeaderValue("justValue")), parseHeaderValue("justValue;=;"))
        assertEquals(listOf(HeaderValue("justValue")), parseHeaderValue("justValue;=33"))
        assertEquals(listOf(HeaderValue("justValue")), parseHeaderValue("justValue;====33"))
        assertEquals(listOf(HeaderValue("justValue")), parseHeaderValue("justValue;=\""))
        assertEquals(
            listOf(HeaderValue("justValue", listOf(HeaderValueParam("x", "")))),
            parseHeaderValue("justValue;x=\"\"")
        )
        assertEquals(
            listOf(HeaderValue("justValue", listOf(HeaderValueParam("x", "\"abc\\")))),
            parseHeaderValue("justValue;x=\"abc\\")
        )
    }

    @Test
    fun parseRealLifeHeadersShouldntFail() {
        val examples = listOf(
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "ru,en-US;q=0.7,en;q=0.3",
            "gzip, deflate",
            """If-Match: "strong", W/"weak", "oops, a \"comma\""""",
            """WWW-Authenticate: Newauth realm="newauth";test="oh, a \"comma\""; foo=a'b'c, Basic realm="basic"""",
            "remixlang=0; remixflash=11.2.202; remixscreen_depth=24; remixdt=0; audio_vol=35; " +
                "remixrefkey=836214a50b5b18f112; audio_time_left=0; remixtst=483196cd; " +
                "remixsid=63476f202634a7b7f6e9975e8b446b126c1d9c82a94e38801bcc3; remixsslsid=1"
        )

        examples.forEach {
            parseHeaderValue(it)
        }
    }

    @Test
    fun parseParametersOnly() {
        assertEquals(
            listOf(HeaderValue("", listOf(HeaderValueParam("k", "v")))),
            parseHeaderValue("k=v", parametersOnly = true)
        )
        assertEquals(
            listOf(HeaderValue("", listOf(HeaderValueParam("k", "v"), HeaderValueParam("k2", "v2")))),
            parseHeaderValue("k=v;k2=v2", parametersOnly = true)
        )
        assertEquals(
            listOf(
                HeaderValue("", listOf(HeaderValueParam("k", "v"))),
                HeaderValue("", listOf(HeaderValueParam("k2", "v2")))
            ),
            parseHeaderValue("k=v,k2=v2", parametersOnly = true)
        )
    }

    @Test
    fun parsingQuotedParamsWorks() {
        assertEquals(
            listOf(
                HeaderValue("", listOf(HeaderValueParam("k", "v"), HeaderValueParam("k2", "v2"))),
            ),
            parseHeaderValue("k=\"v\";k2=\"v2\"", parametersOnly = true)
        )
        assertEquals(
            listOf(
                HeaderValue("", listOf(HeaderValueParam("k", "v"))),
                HeaderValue("", listOf(HeaderValueParam("k2", "v2")))
            ),
            parseHeaderValue("k=\"v\",k2=\"v2\"", parametersOnly = true)
        )
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
        assertEquals(
            "file; k1=v1; k2=v2",
            ContentDisposition.File.withParameters(
                listOf(
                    HeaderValueParam("k1", "v1"),
                    HeaderValueParam("k2", "v2")
                )
            ).toString()
        )
    }

    @Test
    fun testRenderEscaped() {
        assertEquals("file; k=\"v\\nv\"", ContentDisposition.File.withParameter("k", "v\nv").toString())
        assertEquals("file; k=\"v\\rv\"", ContentDisposition.File.withParameter("k", "v\rv").toString())
        assertEquals("file; k=\"v\\tv\"", ContentDisposition.File.withParameter("k", "v\tv").toString())
        assertEquals("file; k=\"v\\\\v\"", ContentDisposition.File.withParameter("k", "v\\v").toString())
        assertEquals("file; k=\"v\\\"v\"", ContentDisposition.File.withParameter("k", "v\"v").toString())
    }

    @Test
    fun testRenderQuoted() {
        val separators = setOf('(', ')', '<', '>', '@', ',', ';', ':', '/', '[', ']', '?', '=', '{', '}', ' ')
        separators.forEach { separator ->
            assertEquals(
                "file; k=\"v${separator}v\"",
                ContentDisposition.File.withParameter("k", "v${separator}v").toString()
            )
        }
    }

    @Test
    fun testRenderDoesNotDoubleQuote() {
        val separators = setOf('(', ')', '<', '>', '@', ',', ';', ':', '/', '[', ']', '?', '=', '{', '}', ' ')
        separators.forEach { separator ->
            assertEquals(
                "file; k=\"v${separator}v\"",
                ContentDisposition.File.withParameter("k", "\"v${separator}v\"").toString()
            )
        }
    }

    @Test
    fun testRenderQuotesIfHasUnescapedQuotes() {
        // first
        assertEquals(
            """file; k="\"\"vv\""""",
            ContentDisposition.File.withParameter("k", """""vv"""").toString()
        )
        // middle
        assertEquals(
            """file; k="\"v\"v\""""",
            ContentDisposition.File.withParameter("k", """"v"v"""").toString()
        )
        // last
        assertEquals(
            """file; k="\"vv\"\""""",
            ContentDisposition.File.withParameter("k", """"vv""""").toString()
        )
        // escaped slash
        assertEquals(
            """file; k="\"v\\\\\"v\""""",
            ContentDisposition.File.withParameter("k", """"v\\"v"""").toString()
        )
    }

    @Test
    fun testDoesNotRenderQuotesIfHasEscapedQuotes() {
        // middle
        assertEquals(
            """file; k="v\"v"""",
            ContentDisposition.File.withParameter("k", """"v\"v"""").toString()
        )
    }

    @Test
    fun headersOfShouldBeCaseInsensitive() {
        val value = "world"
        assertEquals(value, headersOf("hello", value)["HELLO"])
        assertEquals(value, headersOf("hello", listOf(value))["HELLO"])
        assertEquals(value, headersOf("hello" to listOf(value))["HELLO"])
    }

    @Test
    fun headerNamesValidation() {
        val illegalCharacters = "\u0000\u0009\r\n\"(),/:;<=>?@[\\]{}"
        HeadersBuilder().apply {
            append("valid", "ok")

            illegalCharacters.forEach { ch ->
                val key = "not${ch}valid"
                assertFails {
                    append(key, "ok")
                }
                assertFails {
                    set(key, "ok2")
                }
                assertNull(get(key))
            }
        }
    }

    @Test
    fun headersReturnNullWhenMissing() {
        val value = "world"
        val headers1 = headersOf("hello", value)
        val headers2 = headersOf("hello" to listOf(value))

        assertNull(headers1["foo"])
        assertNull(headers2["foo"])

        assertNull(headers1.getAll("foo"))
        assertNull(headers2.getAll("foo"))
    }

    @Test
    fun testSplitSetCookieHeader() {
        assertEquals(listOf("a=b;c,d", "x=0"), "a=b;c,d,x=0".splitSetCookieHeader())
        assertEquals(
            listOf("a=b; Expires=Wed, 21 Oct 2015 07:28:00 GMT", "x=0"),
            "a=b; Expires=Wed, 21 Oct 2015 07:28:00 GMT,x=0".splitSetCookieHeader()
        )
    }

    @Test
    fun headersBuilderTest() {
        val headers = headers {
            append("x", "1")
            appendAll("y", listOf("2", "3"))
        }
        assertEquals(listOf("1"), headers.getAll("x"))
        assertEquals(listOf("2", "3"), headers.getAll("y"))
    }
}
