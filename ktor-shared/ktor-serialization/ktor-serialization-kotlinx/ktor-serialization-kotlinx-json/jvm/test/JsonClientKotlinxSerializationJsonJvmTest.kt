/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.serialization.kotlinx.test.json

import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.*
import kotlin.test.*

class JsonClientKotlinxSerializationJsonJvmTest : AbstractClientContentNegotiationTest() {
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")
    override val webSocketsConverter: WebsocketContentConverter = KotlinxWebsocketSerializationConverter(DefaultJson)

    override fun ContentNegotiation.Config.configureContentNegotiation(contentType: ContentType) {
        json(contentType = contentType) // = KotlinxSerializationJsonJvmConverter
    }

    @Test
    fun testChunkedEncodingByDefault() = testWithEngine(CIO) {
        config {
            install(ContentNegotiation) {
                json()
            }
        }

        test { client ->
            val response = client.post {
                url(port = serverPort, path = "headers")
                setBody(Json("request"))
                contentType(ContentType.Application.Json)
            }.body<String>()

            assertEquals("chunked:null", response)
        }
    }

    @Test
    fun testNotChunkedEncodingIfSet() = testWithEngine(CIO) {
        config {
            install(ContentNegotiation) {
                json(streamRequestBody = false)
            }
        }

        test { client ->
            val response = client.post {
                url(port = serverPort, path = "headers")
                setBody(Json("request"))
                contentType(ContentType.Application.Json)
            }.body<String>()

            assertEquals("null:19", response)
        }
    }

    @Serializable
    data class Json(val value: String)
}
