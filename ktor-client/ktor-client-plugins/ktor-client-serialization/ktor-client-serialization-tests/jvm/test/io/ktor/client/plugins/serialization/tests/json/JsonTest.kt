/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.serialization.tests.json

import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.client.plugins.serialization.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class JsonTest : ClientContentNegotiationTest() {
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")

    override fun ContentNegotiation.Config.registerSerializer(contentType: ContentType) {
        json(Json, contentType)
    }
}
