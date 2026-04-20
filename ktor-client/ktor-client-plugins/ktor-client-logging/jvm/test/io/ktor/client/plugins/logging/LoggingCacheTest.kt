/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.jackson.*
import kotlin.test.*

class LoggingCacheTest {

    data class ResponseBody(val field: String)

    @Test
    fun throwSerializationExceptionWithCachedResponse() = testWithEngine(MockEngine) {
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
}
