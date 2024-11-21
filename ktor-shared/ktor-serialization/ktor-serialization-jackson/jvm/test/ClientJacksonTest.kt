/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.dataformat.smile.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.jackson.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.test.*

class ClientJacksonTest : AbstractClientContentNegotiationTest() {
    private val converter = JacksonConverter()

    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")
    override val webSocketsConverter: WebsocketContentConverter = JacksonWebsocketContentConverter()

    private val smileContentType = ContentType.parse("application/x-jackson-smile")

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        register(contentType, converter)
    }

    override fun createRoutes(route: Route): Unit = with(route) {
        super.createRoutes(route)

        post("/jackson") {
            assertEquals("""{"value":"request"}""", call.receive())
            call.respondText(
                """{"ok":true,"result":[{"value":"response","ignoredValue":"not_ignored"}]}""",
                ContentType.Application.Json
            )
        }
        post("/headers") {
            call.respondText(
                "${call.request.headers[HttpHeaders.TransferEncoding]}" +
                    ":" +
                    "${call.request.headers[HttpHeaders.ContentLength]}"
            )
        }
        post("/smile") {
            val input = call.receiveStream()

            val mapper = ObjectMapper(SmileFactory())
            val data = mapper.readValue(input, jacksonTypeRef<Map<*, *>>())
            assertEquals(mapOf("value" to "request"), data)

            val response = mapOf(
                "ok" to true,
                "result" to listOf(mapOf("value" to "response", "ignoredValue" to "not_ignored"))
            )

            call.respondBytes(mapper.writeValueAsBytes(response), smileContentType)
        }
    }

    @Test
    fun testJackson() = testWithEngine(CIO) {
        configureClient()

        test { client ->
            val response = client.post {
                url(port = serverPort, path = "jackson")
                setBody(Jackson("request", "ignored"))
                contentType(ContentType.Application.Json)
            }.body<Response<List<Jackson>>>()

            assertTrue(response.ok)
            val list = response.result!!
            assertEquals(1, list.size)
            assertEquals(Jackson("response", null), list[0]) // encoded with GsonConverter
        }
    }

    @Test
    fun testChunkedEncodingByDefault() = testWithEngine(CIO) {
        config {
            install(ContentNegotiation) {
                jackson()
            }
        }

        test { client ->
            val response = client.post {
                url(port = serverPort, path = "headers")
                setBody(Jackson("request", "ignored"))
                contentType(ContentType.Application.Json)
            }.body<String>()

            assertEquals("chunked:null", response)
        }
    }

    @Test
    fun testNotChunkedEncodingIfSet() = testWithEngine(CIO) {
        config {
            install(ContentNegotiation) {
                jackson(streamRequestBody = false)
            }
        }

        test { client ->
            val response = client.post {
                url(port = serverPort, path = "headers")
                setBody(Jackson("request", "ignored"))
                contentType(ContentType.Application.Json)
            }.body<String>()

            assertEquals("null:19", response)
        }
    }

    @Test
    fun testSmileEncoding() = testWithEngine(CIO) {
        val smileMapper = ObjectMapper(SmileFactory()).apply {
            registerKotlinModule()
        }

        config {
            install(ContentNegotiation) {
                register(smileContentType, JacksonConverter(smileMapper))
            }
        }

        test { client ->
            val response = client.post {
                url(port = serverPort, path = "smile")
                setBody(Jackson("request", "ignored"))
                contentType(smileContentType)
                accept(smileContentType)
            }.body<Response<List<Jackson>>>()

            assertTrue(response.ok)
            val list = response.result!!
            assertEquals(1, list.size)
            assertEquals(Jackson("response", null), list[0]) // encoded with GsonConverter
        }
    }

    @Test
    @Ignore
    override fun testSealed() {
    }

    data class Jackson(val value: String, @JsonIgnore val ignoredValue: String?)
}
