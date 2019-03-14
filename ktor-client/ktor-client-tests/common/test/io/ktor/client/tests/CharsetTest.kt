package io.ktor.client.tests

import io.ktor.client.engine.mock.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.io.charsets.*
import kotlin.test.*


class CharsetTest {

    @Test
    fun testDefaultCharset() = clientTest(MockEngine) {
        config {
            engine {
                // get handler
                addHandler { request ->
                    assertEquals("UTF-8", request.headers[HttpHeaders.AcceptCharset])

                    request.response("Content", HttpStatusCode.OK, buildHeaders {
                        append(HttpHeaders.ContentType, ContentType.Text.Plain.withCharset(Charsets.UTF_8))
                    })
                }

                // post handler
                addHandler { request ->
                    assertEquals("UTF-8", request.headers[HttpHeaders.AcceptCharset])

                    val requestContent = request.content as TextContent
                    assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), requestContent.contentType)
                    assertEquals("Hello, Test!", requestContent.text)

                    request.responseOk()
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
    fun testCharsetsWithoutQuality() = clientTest(MockEngine) {
        config {
            engine {
                // get handler
                addHandler { request ->
                    assertEquals("ISO-8859-1,UTF-8", request.headers[HttpHeaders.AcceptCharset])

                    request.response("Content", HttpStatusCode.OK, buildHeaders {
                        append(HttpHeaders.ContentType, ContentType.Text.Plain.withCharset(Charsets.UTF_8))
                    })
                }

                // post handler
                addHandler { request ->
                    assertEquals("ISO-8859-1,UTF-8", request.headers[HttpHeaders.AcceptCharset])

                    val requestContent = request.content as TextContent
                    assertEquals(ContentType.Text.Plain.withCharset(Charsets.ISO_8859_1), requestContent.contentType)
                    assertEquals("Hello, Test!", requestContent.text)

                    request.responseOk()
                }
            }

            Charsets {
                accept(Charsets.UTF_8)
                accept(Charsets.ISO_8859_1)
            }
        }

        test { client ->
            val response = client.get<String>()
            assertEquals("Content", response)
            client.post<Unit>(body = "Hello, Test!")
        }
    }

    @Test
    fun testCharsetsWithQuality() = clientTest(MockEngine) {
        config {
            engine {
                // get handler
                addHandler { request ->
                    assertEquals("ISO-8859-1;q=0.9,UTF-8;q=0.1", request.headers[HttpHeaders.AcceptCharset])

                    request.response("Content", HttpStatusCode.OK, buildHeaders {
                        append(HttpHeaders.ContentType, ContentType.Text.Plain.withCharset(Charsets.ISO_8859_1))
                    })
                }

                // post handler
                addHandler { request ->
                    assertEquals("ISO-8859-1;q=0.9,UTF-8;q=0.1", request.headers[HttpHeaders.AcceptCharset])

                    val requestContent = request.content as TextContent
                    assertEquals(ContentType.Text.Plain.withCharset(Charsets.ISO_8859_1), requestContent.contentType)
                    assertEquals("Hello, Test!", requestContent.text)

                    request.responseOk()
                }
            }

            Charsets {
                accept(Charsets.UTF_8, quality = 0.1f)
                accept(Charsets.ISO_8859_1, quality = 0.9f)
            }
        }

        test { client ->
            val response = client.get<String>()
            assertEquals("Content", response)
            client.post<Unit>(body = "Hello, Test!")
        }
    }

    @Test
    fun testCharsetMixedQuality() = clientTest(MockEngine) {
        config {
            engine {
                // get handler
                addHandler { request ->
                    assertEquals("ISO-8859-1,UTF-8;q=0.1", request.headers[HttpHeaders.AcceptCharset])

                    request.response("Content", HttpStatusCode.OK, buildHeaders {
                        append(HttpHeaders.ContentType, ContentType.Text.Plain.withCharset(Charsets.ISO_8859_1))
                    })
                }

                // post handler
                addHandler { request ->
                    assertEquals("ISO-8859-1,UTF-8;q=0.1", request.headers[HttpHeaders.AcceptCharset])

                    val requestContent = request.content as TextContent
                    assertEquals(ContentType.Text.Plain.withCharset(Charsets.ISO_8859_1), requestContent.contentType)
                    assertEquals("Hello, Test!", requestContent.text)

                    request.responseOk()
                }
            }


            Charsets {
                accept(Charsets.UTF_8, quality = 0.1f)
                accept(Charsets.ISO_8859_1)
            }
        }

        test { client ->
            val response = client.get<String>()
            assertEquals("Content", response)
            client.post<Unit>(body = "Hello, Test!")
        }
    }
}
