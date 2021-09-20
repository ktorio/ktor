/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.serialization.tests.cbor

import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.client.plugins.serialization.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*
import kotlinx.serialization.json.*

@OptIn(ExperimentalSerializationApi::class)
class CborTest : ClientContentNegotiationTest() {
    override val defaultContentType: ContentType = ContentType.Application.Cbor
    override val customContentType: ContentType = ContentType.parse("application/x-cbor")

    @OptIn(InternalSerializationApi::class)
    override fun ContentNegotiation.Config.registerSerializer(contentType: ContentType) {
        cbor(Cbor, contentType)
    }

    @OptIn(InternalSerializationApi::class)
    override suspend fun <T : Any> ApplicationCall.respond(
        responseJson: String,
        contentType: ContentType,
        serializer: KSerializer<T>
    ) {
        val actual = Json.decodeFromString(serializer, responseJson)
        val bytes = Cbor.encodeToByteArray(serializer, actual)
        respondBytes(bytes, contentType)
    }

    override suspend fun ApplicationCall.respondWithRequestBody(contentType: ContentType) {
        respondBytes(receive(), contentType)
    }
}
