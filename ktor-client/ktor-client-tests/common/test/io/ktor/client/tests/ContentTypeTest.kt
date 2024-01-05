/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.charsets.*
import kotlin.test.*

class ContentTypeTest {

    @Test
    fun testSendStringWithContentType() = testWithEngine(MockEngine) {
        val contentType = ContentType.Text.Plain.withParameter("hello", "world")

        config {
            engine {
                addHandler { request ->
                    val body = request.body
                    assertEquals(
                        contentType.withCharset(Charsets.UTF_8),
                        body.contentType
                    )

                    assertTrue(body is TextContent)
                    assertEquals("Hello, World", body.text)
                    respond("OK")
                }
            }
        }

        test { client ->
            client.get("/") {
                header(HttpHeaders.ContentType, contentType)
                setBody("Hello, World")
            }
        }
    }

    @Test
    fun testSendStringWithoutContentType() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler { request ->
                    val body = request.body
                    assertEquals(
                        ContentType.Text.Plain.withCharset(Charsets.UTF_8),
                        body.contentType
                    )

                    assertTrue(body is TextContent)
                    assertEquals("Hello, World", body.text)
                    respond("OK")
                }
            }
        }

        test { client ->
            client.get("/") {
                setBody("Hello, World")
            }
        }
    }

    @Test
    fun testSendStringAsBytesWithoutConversion() = testWithEngine(MockEngine) {
        config {
            engine {
                addHandler {
                    error("Unreachable: ${it.body}")
                }
            }
        }

        test { client ->
            assertFailsWith<IllegalStateException> {
                client.get("/") {
                    setBody("Hello, World")
                    header(HttpHeaders.ContentType, ContentType.Application.OctetStream)
                }
            }
        }
    }

    @Test
    fun testEmptyBodyWithContentType() = testSuspend {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("application/protobuf", request.headers[HttpHeaders.ContentType])
                    respond("OK")
                }
            }
        }

        client.post("/") {
            header(HttpHeaders.ContentType, ContentType.Application.ProtoBuf)
        }
    }
}
