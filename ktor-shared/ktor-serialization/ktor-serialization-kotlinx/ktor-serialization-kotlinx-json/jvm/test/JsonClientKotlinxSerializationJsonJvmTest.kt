/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.serialization.kotlinx.test.json

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*

class JsonClientKotlinxSerializationJsonJvmTest : AbstractClientContentNegotiationTest() {
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")
    override val webSocketsConverter: WebsocketContentConverter = KotlinxWebsocketSerializationConverter(DefaultJson)

    override fun ContentNegotiation.Config.configureContentNegotiation(contentType: ContentType) {
        json(contentType = contentType) // = KotlinxSerializationJsonJvmConverter
    }
}
