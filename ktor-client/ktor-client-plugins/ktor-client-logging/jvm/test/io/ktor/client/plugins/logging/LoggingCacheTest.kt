/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.job
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoggingCacheTest {

    data class ResponseBody(val field: String)

    @Test
    fun `throw SerializationException with cached response`() = testWithEngine(MockEngine) {
        config {
            install(Logging) {
            }
            install(ContentNegotiation) {
                jackson()
            }
            install(HttpCache)
            engine {
                addHandler {
                    respond(
                        content = "not valid json",
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                            HttpHeaders.CacheControl to listOf("max-age=60")
                        )
                    )
                }
            }
        }

        test { client ->
            assertFailsWith<JsonConvertException> {
                client.get("/cached").body<ResponseBody>()
            }

            assertFailsWith<JsonConvertException> {
                client.get("/cached").body<ResponseBody>()
            }
        }
    }

    @Test
    fun `logs body of revalidated streaming response`() = testWithEngine(MockEngine) {
        val logs = mutableListOf<String>()

        config {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        logs.add(message)
                    }
                }
                level = LogLevel.BODY
            }
            install(HttpCache)
            engine {
                addHandler { request ->
                    val cacheHeaders = headersOf(
                        HttpHeaders.ETag to listOf("\"v1\""),
                        HttpHeaders.CacheControl to listOf("no-cache")
                    )
                    if (request.headers.contains(HttpHeaders.IfNoneMatch)) {
                        respond(content = "", status = HttpStatusCode.NotModified, headers = cacheHeaders)
                    } else {
                        respond(content = "cached body", headers = cacheHeaders)
                    }
                }
            }
        }

        test { client ->
            assertEquals("cached body", client.get("/revalidated").bodyAsText())
            assertEquals("cached body", client.prepareGet("/revalidated").execute { it.bodyAsText() })

            // Streaming responses are logged asynchronously by ResponseObserver in the client scope
            client.close()
            client.coroutineContext.job.join()
            assertEquals(2, logs.count { "cached body" in it }, "Body should be logged for both responses: $logs")
        }
    }
}
