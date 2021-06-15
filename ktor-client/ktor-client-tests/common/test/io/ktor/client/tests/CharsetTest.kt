/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.engine.mock.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.charsets.*
import kotlin.test.*

class CharsetTest {

    @Test
    fun testDefaultCharset() = testWithEngine(MockEngine) {
        config {
            engine {
                // get handler
                addHandler { request ->
                    assertEquals("UTF-8", request.headers[HttpHeaders.AcceptCharset])

                    respond(
                        "Content",
                        HttpStatusCode.OK,
                        buildHeaders {
                            append(HttpHeaders.ContentType, ContentType.Text.Plain.withCharset(Charsets.UTF_8))
                        }
                    )
                }

                // post handler
                addHandler { request ->
                    assertEquals("UTF-8", request.headers[HttpHeaders.AcceptCharset])

                    val requestContent = request.body as TextContent
                    assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), requestContent.contentType)
                    assertEquals("Hello, Test!", requestContent.text)

                    respondOk()
                }
            }
        }

        test { client ->
            val response = client.get<String>()
            assertEquals("Content", response)
            client.post<Unit>(body = "Hello, Test!")
        }
    }

    @Test
    fun testCharsetsWithoutQuality() = testWithEngine(MockEngine) {
        config {
            engine {
                // get handler
                addHandler { request ->
                    assertEquals("ISO-8859-1,UTF-8", request.headers[HttpHeaders.AcceptCharset])

                    respond(
                        "Content",
                        HttpStatusCode.OK,
                        buildHeaders {
                            append(HttpHeaders.ContentType, ContentType.Text.Plain.withCharset(Charsets.UTF_8))
                        }
                    )
                }

                // post handler
                addHandler { request ->
                    assertEquals("ISO-8859-1,UTF-8", request.headers[HttpHeaders.AcceptCharset])

                    val requestContent = request.body as TextContent
                    assertEquals(ContentType.Text.Plain.withCharset(Charsets.ISO_8859_1), requestContent.contentType)
                    assertEquals("Hello, Test!", requestContent.text)

                    respondOk()
                }
            }

            Charsets {
                register(Charsets.UTF_8)
                register(Charsets.ISO_8859_1)
            }
        }

        test { client ->
            val response = client.get<String>()
            assertEquals("Content", response)
            client.post<Unit>(body = "Hello, Test!")
        }
    }

    @Test
    fun testCharsetsWithQuality() = testWithEngine(MockEngine) {
        config {
            engine {
                // get handler
                addHandler { request ->
                    assertEquals("ISO-8859-1;q=0.9,UTF-8;q=0.1", request.headers[HttpHeaders.AcceptCharset])

                    respond(
                        "Content",
                        HttpStatusCode.OK,
                        buildHeaders {
                            append(HttpHeaders.ContentType, ContentType.Text.Plain.withCharset(Charsets.ISO_8859_1))
                        }
                    )
                }

                // post handler
                addHandler { request ->
                    assertEquals("ISO-8859-1;q=0.9,UTF-8;q=0.1", request.headers[HttpHeaders.AcceptCharset])

                    val requestContent = request.body as TextContent
                    assertEquals(ContentType.Text.Plain.withCharset(Charsets.ISO_8859_1), requestContent.contentType)
                    assertEquals("Hello, Test!", requestContent.text)

                    respondOk()
                }
            }

            Charsets {
                register(Charsets.UTF_8, quality = 0.1f)
                register(Charsets.ISO_8859_1, quality = 0.9f)
            }
        }

        test { client ->
            val response = client.get<String>()
            assertEquals("Content", response)
            client.post<Unit>(body = "Hello, Test!")
        }
    }

    @Test
    fun testCharsetMixedQuality() = testWithEngine(MockEngine) {
        config {
            engine {
                // get handler
                addHandler { request ->
                    assertEquals("ISO-8859-1,UTF-8;q=0.1", request.headers[HttpHeaders.AcceptCharset])

                    respond(
                        "Content",
                        HttpStatusCode.OK,
                        buildHeaders {
                            append(HttpHeaders.ContentType, ContentType.Text.Plain.withCharset(Charsets.ISO_8859_1))
                        }
                    )
                }

                // post handler
                addHandler { request ->
                    assertEquals("ISO-8859-1,UTF-8;q=0.1", request.headers[HttpHeaders.AcceptCharset])

                    val requestContent = request.body as TextContent
                    assertEquals(ContentType.Text.Plain.withCharset(Charsets.ISO_8859_1), requestContent.contentType)
                    assertEquals("Hello, Test!", requestContent.text)

                    respondOk()
                }
            }

            Charsets {
                register(Charsets.UTF_8, quality = 0.1f)
                register(Charsets.ISO_8859_1)
            }
        }

        test { client ->
            val response = client.get<String>()
            assertEquals("Content", response)
            client.post<Unit>(body = "Hello, Test!")
        }
    }

    @Test
    fun testIllegalCharset() = testWithEngine(MockEngine) {
        config {
            engine {
                // get handler
                addHandler { request ->
                    assertEquals("UTF-8", request.headers[HttpHeaders.AcceptCharset])

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
            val response = client.get<String>()
            assertEquals("Content", response)
        }
    }

    @Test
    fun testUnsupportedCharset() = testWithEngine(MockEngine) {
        config {
            engine {
                // get handler
                addHandler { request ->
                    assertEquals("UTF-8", request.headers[HttpHeaders.AcceptCharset])

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
            val response = client.get<String>()
            assertEquals("Content", response)
        }
    }
}
