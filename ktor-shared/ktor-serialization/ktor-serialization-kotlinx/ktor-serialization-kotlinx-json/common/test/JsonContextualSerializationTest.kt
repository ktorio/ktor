/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.test.json

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.test.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*

class JsonContextualSerializationTest : AbstractContextualSerializationTest<Json>() {
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val defaultSerializationFormat: Json = DefaultJson

    override fun buildContextualSerializer(context: SerializersModule): Json = Json { serializersModule = context }

    override fun assertEquals(
        expectedAsJson: String,
        actual: ByteArray,
        format: Json,
        serializer: KSerializer<*>
    ): Boolean {
        return expectedAsJson == actual.decodeToString()
    }
}
