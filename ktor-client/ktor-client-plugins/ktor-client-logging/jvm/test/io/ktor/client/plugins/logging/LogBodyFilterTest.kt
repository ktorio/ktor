/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.logging

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import kotlinx.io.Buffer
import kotlinx.io.writeString
import kotlin.test.*

class LogBodyFilterTest {

    @Test
    fun testCustomBodyFilterResults() = testApplication {
        val logs = mutableListOf<String>()
        val testLogger = object : Logger {
            override fun log(message: String) {
                logs.add(message)
            }
        }

        application {
            routing {
                get("/{type}") {
                    val type = call.parameters["type"]
                    call.respondText(
                        "original content",
                        ContentType.parse("application/$type")
                    )
                }
            }
        }

        val testClient = createClient {
            install(Logging) {
                logger = testLogger
                level = LogLevel.ALL
                format = LoggingFormat.OkHttp
                bodyFilter = CommonLogBodyFilter { _, contentType, _, _ ->
                    when (contentType?.contentSubtype) {
                        "empty" -> BodyFilterResult.Empty
                        "skipped" -> BodyFilterResult.Skip("custom reason", 123L)
                        "buffered" -> {
                            val buffer = Buffer().also { it.writeString("filtered content") }
                            BodyFilterResult.BufferContent(buffer, Charsets.UTF_8)
                        }
                        else -> BodyFilterResult.Skip("unsupported")
                    }
                }
            }
        }

        // empty responses
        testClient.get("/empty").bodyAsText()
        assertTrue(logs.any { it.contains("0-byte body") }, "Should log empty body")

        // skipped responses
        logs.clear()
        testClient.get("/skipped").bodyAsText()
        assertTrue(logs.any { it.contains("custom reason 123-byte body omitted") }, "Should log skipped with reason")

        // logged content
        logs.clear()
        testClient.get("/buffered").bodyAsText()
        assertTrue(logs.any { it.contains("filtered content") }, "Should log filtered content")
        assertTrue(logs.none { it.contains("original content") }, "Should not log the original content")
    }
}
