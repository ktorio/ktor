/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.common.serialization.*
import io.ktor.common.serialization.kotlinx.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.test.dispatcher.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import kotlin.test.*

@Serializable
data class UserData(val id: Int, val name: String)

object UserDataSerializer : KSerializer<UserData> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("UserData")

    override fun deserialize(decoder: Decoder): UserData {
        val id = decoder.decodeString().toInt(16)
        val name = decoder.decodeString()
        return UserData(id, name)
    }

    override fun serialize(encoder: Encoder, value: UserData) {
        encoder.encodeString(value.id.toString(16))
        encoder.encodeString(value.name)
    }
}

class ContextualSerializationTest {

    @Test
    fun testSerializationWithContext() = testSuspend {
        val context = serializersModuleOf(UserData::class, UserDataSerializer)
        val contextual = KotlinxSerializationConverter(Json { serializersModule = context })
        val simple = KotlinxSerializationConverter()

        val data = UserData(1, "kotlin")

        val contextualString = contextual.testSerialize(data)
        val simpleString = simple.testSerialize(data)

        assertEquals("\"1\"\"kotlin\"", contextualString)
        assertEquals("{\"id\":1,\"name\":\"kotlin\"}", simpleString)

        assertEquals(
            data,
            contextual.deserialize(Charsets.UTF_8, typeInfo<UserData>(), ByteReadChannel(contextualString))
        )
        assertEquals(
            data,
            simple.deserialize(Charsets.UTF_8, typeInfo<UserData>(), ByteReadChannel(simpleString))
        )
    }

    private suspend inline fun <reified T : Any> ContentConverter.testSerialize(data: T): String {
        val content = serialize(ContentType.Application.Json, Charsets.UTF_8, typeInfo<T>(), data)
        return (content as? TextContent)?.text ?: error("Failed to get serialized $data")
    }
}
