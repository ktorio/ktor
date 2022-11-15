/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.gson.*
import kotlin.test.*

class ClientGsonTest : AbstractClientContentNegotiationTest() {
    private val converter = GsonConverter()

    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")
    override val webSocketsConverter: WebsocketContentConverter = GsonWebsocketContentConverter()

    override fun ContentNegotiation.Config.configureContentNegotiation(contentType: ContentType) {
        register(contentType, converter)
    }

    @Test
    fun testChunkedEncodingByDefault() = testWithEngine(CIO) {
        config {
            install(ContentNegotiation) {
                gson()
            }
        }

        test { client ->
            val response = client.post {
                url(port = serverPort, path = "headers")
                setBody(Gson("request"))
                contentType(ContentType.Application.Json)
            }.body<String>()

            assertEquals("chunked:null", response)
        }
    }

    @Test
    fun testNotChunkedEncodingIfSet() = testWithEngine(CIO) {
        config {
            install(ContentNegotiation) {
                gson(streamRequestBody = false)
            }
        }

        test { client ->
            val response = client.post {
                url(port = serverPort, path = "headers")
                setBody(Gson("request"))
                contentType(ContentType.Application.Json)
            }.body<String>()

            assertEquals("null:19", response)
        }
    }

    @Test
    @Ignore
    override fun testSealed() {}

    data class Gson(val value: String)
}
