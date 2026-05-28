/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx

import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class Payload(val value: String)

class ConverterTest {

    @Test
    fun returnsNullForEmptyChannelWithDelayedClose() = runTest {
        val channel = ByteChannel()
        launch {
            delay(200.milliseconds)
            channel.close()
        }

        val converter = KotlinxSerializationConverter(DefaultJson)
        val result = converter.deserialize(Charsets.UTF_8, typeInfo<Payload>(), channel)
        assertNull(result)
    }

    @Test
    fun `returns null for empty channel without extensions`() = runTest {
        val channel = ByteChannel()
        launch {
            delay(200.milliseconds)
            channel.close()
        }

        val converter = KotlinxSerializationConverter(EmptyExtensionStringFormat)
        val result = converter.deserialize(Charsets.UTF_8, typeInfo<Payload?>(), channel)

        assertNull(result)
    }

    private object EmptyExtensionStringFormat : StringFormat {
        override val serializersModule: SerializersModule = EmptySerializersModule()

        override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String =
            error("Not used")

        override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T =
            fail("Empty content should be handled before decoding, got: '$string'")
    }
}
