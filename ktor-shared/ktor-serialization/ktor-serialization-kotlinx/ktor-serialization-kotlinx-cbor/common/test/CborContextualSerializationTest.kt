/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.test.json

import io.ktor.http.*
import io.ktor.serialization.kotlinx.cbor.*
import io.ktor.serialization.kotlinx.test.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*

@OptIn(ExperimentalSerializationApi::class)
class CborContextualSerializationTest : AbstractContextualSerializationTest<Cbor>() {
    override val defaultContentType: ContentType = ContentType.Application.Cbor
    override val defaultSerializationFormat: Cbor = DefaultCbor

    override fun buildContextualSerializer(context: SerializersModule): Cbor = Cbor { serializersModule = context }

    @Suppress("JSON_FORMAT_REDUNDANT")
    override fun assertEquals(
        expectedAsJson: String,
        actual: ByteArray,
        format: Cbor,
        serializer: KSerializer<*>,
    ): Boolean {
        val expected = Json { serializersModule = format.serializersModule }
            .decodeFromString(serializer, expectedAsJson)
        return expected == format.decodeFromByteArray(serializer, actual)
    }
}
