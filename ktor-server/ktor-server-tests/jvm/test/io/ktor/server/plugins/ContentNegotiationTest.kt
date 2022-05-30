/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import java.io.*
import kotlin.test.*

@Suppress("DEPRECATION")
class ContentNegotiationTest {

    private val alwaysFailingConverter = object : ContentConverter {
        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any
        ): OutgoingContent? {
            fail("This converter should be never started for send")
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
            fail("This converter should be never started for receive")
        }
    }

    @Test
    fun testReceiveInputStreamTransformedByDefault(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            // Order here matters. The first registered content type matching the Accept header will be chosen.
            register(ContentType.Any, alwaysFailingConverter)
        }

        application.routing {
            post("/input-stream") {
                val size = call.receive<InputStream>().readBytes().size
                call.respondText("bytes from IS: $size")
            }
            post("/multipart") {
                val multipart = call.receiveMultipart()
                val parts = multipart.readAllParts()
                call.respondText("parts: ${parts.map { it.name }}")
            }
        }

        handleRequest(HttpMethod.Post, "/input-stream", { setBody("123") }).let { call ->
            assertEquals("bytes from IS: 3", call.response.content)
        }

        handleRequest(HttpMethod.Post, "/multipart") {
            setBody(
                "my-boundary",
                listOf(
                    PartData.FormItem(
                        "test",
                        {},
                        headersOf(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition(
                                "form-data",
                                listOf(
                                    HeaderValueParam("name", "field1")
                                )
                            ).toString()
                        )
                    )
                )
            )
            addHeader(
                HttpHeaders.ContentType,
                ContentType.MultiPart.FormData.withParameter("boundary", "my-boundary").toString()
            )
        }.let { call ->
            assertEquals("parts: [field1]", call.response.content)
        }
    }

    @Test
    fun testRespondByteArray() = testApplication {
        application {
            routing {
                install(ContentNegotiation) {
                    register(ContentType.Application.Json, alwaysFailingConverter)
                }
                get("/") {
                    call.respond("test".toByteArray())
                }
            }
        }
        val response = client.get("/").body<ByteArray>()
        assertContentEquals("test".toByteArray(), response)
    }
}
