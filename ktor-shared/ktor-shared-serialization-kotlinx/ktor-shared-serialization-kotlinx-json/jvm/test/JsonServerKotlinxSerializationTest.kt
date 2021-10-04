/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.shared.serialization.kotlinx.test.json

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.shared.serialization.kotlinx.json.*
import io.ktor.shared.serialization.kotlinx.test.*

class JsonServerKotlinxSerializationTest : AbstractServerSerializationTest() {
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")

    override fun ContentNegotiation.Configuration.configureContentNegotiation(contentType: ContentType) {
        json(contentType = contentType)
    }

    override fun simpleDeserialize(t: ByteArray): TestEntity {
        return DefaultJson.decodeFromString(serializer, String(t))
    }

    override fun simpleSerialize(any: TestEntity): ByteArray {
        return DefaultJson.encodeToString(serializer, any).toByteArray()
    }
}
