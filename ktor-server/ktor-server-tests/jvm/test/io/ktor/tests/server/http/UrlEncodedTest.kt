/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.http

import io.ktor.http.*
import io.ktor.request.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlin.test.*

class UrlEncodedTest {
    fun ApplicationRequest.parseUrlEncodedParameters(limit: Int = 1000): Parameters {
        return runBlocking {
            call.receiveText().parseUrlEncodedParameters(contentCharset() ?: Charsets.UTF_8, limit)
        }
    }

    @Test
    fun `should parse simple with no headers`() {
        withTestApplication {
            createCall {
                setBody("field1=%D0%A2%D0%B5%D1%81%D1%82")

                val parsed = parseUrlEncodedParameters()
                assertEquals("\u0422\u0435\u0441\u0442", parsed["field1"])
            }
        }
    }

    @Test
    fun `should parse simple with no encoding`() {
        withTestApplication {
            createCall {
                setBody("field1=%D0%A2%D0%B5%D1%81%D1%82")
                addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded")

                val parsed = parseUrlEncodedParameters()
                assertEquals("\u0422\u0435\u0441\u0442", parsed["field1"])
            }
        }
    }

    @Test
    fun `should parse simple with specified encoding utf8`() {
        withTestApplication {
            createCall {
                setBody("field1=%D0%A2%D0%B5%D1%81%D1%82")
                addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded; charset=utf-8")

                val parsed = parseUrlEncodedParameters()
                assertEquals("\u0422\u0435\u0441\u0442", parsed["field1"])
            }
        }
    }

    @Test
    fun `should parse simple with specified encoding non utf`() {
        withTestApplication {
            createCall {
                setBody("field1=%D2%E5%F1%F2")
                addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded; charset=windows-1251")

                val parsed = parseUrlEncodedParameters()
                assertEquals("\u0422\u0435\u0441\u0442", parsed["field1"])
            }
        }
    }

    @Test
    fun `should parse simple with specified encoding non utf in parameter`() {
        withTestApplication {
            createCall {
                setBody("field1=%D2%E5%F1%F2&_charset_=windows-1251")
                addHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded")

                val parsed = parseUrlEncodedParameters()
                assertEquals("\u0422\u0435\u0441\u0442", parsed["field1"])
            }
        }
    }

    @Test
    fun testRenderUrlEncoded() {
        assertEquals("p1=a+b", listOf("p1" to "a b").formUrlEncode())
        assertEquals("p%3D1=a%3Db", listOf("p=1" to "a=b").formUrlEncode())
        assertEquals("p1=a&p1=b&p2=c", listOf("p1" to "a", "p1" to "b", "p2" to "c").formUrlEncode())
    }

    @Test
    fun testRenderUrlEncodedStringValues() {
        assertEquals("p1=a+b", parametersOf("p1", listOf("a b")).formUrlEncode())
        assertEquals("p%3D1=a%3Db", parametersOf("p=1", listOf("a=b")).formUrlEncode())
        assertEquals("p1=a&p1=b&p2=c", parametersOf("p1" to listOf("a", "b"), "p2" to listOf("c")).formUrlEncode())
    }
}
