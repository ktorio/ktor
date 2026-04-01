/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.charsets.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CharsetTest {

    @Test
    fun testDefaultCharset() = testCharsetScenario(
        expectedAcceptCharset = null,
        expectedRequestCharset = Charsets.UTF_8,
    )

    @Test
    fun testOnlyUtf8Charset() = testCharsetScenario(
        expectedAcceptCharset = null,
        expectedRequestCharset = Charsets.UTF_8,
    ) {
        Charsets {
            register(Charsets.UTF_8)
        }
    }

    @Test
    fun testOnlyUtf8CharsetWithQuality() = testCharsetScenario(
        expectedAcceptCharset = null,
        expectedRequestCharset = Charsets.UTF_8,
    ) {
        Charsets {
            register(Charsets.UTF_8, 0.8f)
        }
    }

    @Test
    fun testCharsetsWithoutQuality() = testCharsetScenario(
        expectedAcceptCharset = "ISO-8859-1,UTF-8",
        expectedRequestCharset = Charsets.ISO_8859_1,
        responseCharset = Charsets.UTF_8
    ) {
        Charsets {
            register(Charsets.UTF_8)
            register(Charsets.ISO_8859_1)
        }
    }

    @Test
    fun testCharsetsWithQuality() = testCharsetScenario(
        expectedAcceptCharset = "ISO-8859-1;q=0.9,UTF-8;q=0.1",
        expectedRequestCharset = Charsets.ISO_8859_1,
    ) {
        Charsets {
            register(Charsets.UTF_8, quality = 0.1f)
            register(Charsets.ISO_8859_1, quality = 0.9f)
        }
    }

    @Test
    fun testCharsetMixedQuality() = testCharsetScenario(
        expectedAcceptCharset = "ISO-8859-1,UTF-8;q=0.1",
        expectedRequestCharset = Charsets.ISO_8859_1,
    ) {
        Charsets {
            register(Charsets.UTF_8, quality = 0.1f)
            register(Charsets.ISO_8859_1)
        }
    }

    private fun testCharsetScenario(
        expectedAcceptCharset: String?,
        expectedRequestCharset: Charset,
        responseCharset: Charset = expectedRequestCharset,
        charsetConfig: HttpClientConfig<MockEngineConfig>.() -> Unit = {}
    ) = testWithEngine(MockEngine) {
        config {
            engine {
                // GET handler
                addHandler { request ->
                    if (expectedAcceptCharset == null) {
                        assertAcceptCharsetNotSet(request)
                    } else {
                        assertEquals(expectedAcceptCharset, request.headers[HttpHeaders.AcceptCharset])
                    }

                    respond(
                        "Content",
                        HttpStatusCode.OK,
                        buildHeaders {
                            append(HttpHeaders.ContentType, ContentType.Text.Plain.withCharset(responseCharset))
                        }
                    )
                }

                // POST handler
                addHandler { request ->
                    if (expectedAcceptCharset == null) {
                        assertAcceptCharsetNotSet(request)
                    } else {
                        assertEquals(expectedAcceptCharset, request.headers[HttpHeaders.AcceptCharset])
                    }

                    val requestContent = request.body as TextContent
                    assertEquals(ContentType.Text.Plain.withCharset(expectedRequestCharset), requestContent.contentType)
                    assertEquals("Hello, Test!", requestContent.text)

                    respondOk()
                }
            }

            charsetConfig()
        }

        test { client ->
            val response = client.get {}.body<String>()
            assertEquals("Content", response)
            client.post { setBody("Hello, Test!") }
        }
    }

    @Test
    fun testIllegalCharset() = testWithEngine(MockEngine) {
        config {
            engine {
                // get handler
                addHandler { request ->
                    assertAcceptCharsetNotSet(request)

                    respond(
                        "Content",
                        HttpStatusCode.OK,
                        buildHeaders {
                            append(
                                HttpHeaders.ContentType,
                                ContentType.Text.Plain.withParameter("charset", "%s")
                            )
                        }
                    )
                }
            }
        }

        test { client ->
            val response = client.get { }.bodyAsText()
            assertEquals("Content", response)
        }
    }

    @Test
    fun testUnsupportedCharset() = testWithEngine(MockEngine) {
        config {
            engine {
                // get handler
                addHandler { request ->
                    assertAcceptCharsetNotSet(request)

                    respond(
                        "Content",
                        HttpStatusCode.OK,
                        buildHeaders {
                            append(
                                HttpHeaders.ContentType,
                                ContentType.Text.Plain.withParameter("charset", "abracadabra-encoding")
                            )
                        }
                    )
                }
            }
        }

        test { client ->
            val response = client.get { }.bodyAsText()
            assertEquals("Content", response)
        }
    }

    private fun assertAcceptCharsetNotSet(request: HttpRequestData) {
        assertNull(request.headers[HttpHeaders.AcceptCharset], "Accept-Charset header should not be set")
    }
}
