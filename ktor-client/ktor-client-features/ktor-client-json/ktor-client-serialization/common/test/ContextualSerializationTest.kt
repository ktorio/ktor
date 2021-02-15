/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
import io.ktor.client.features.json.serializer.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.core.*
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
    fun testSerializationWithContext() {
        val context = serializersModuleOf(UserData::class, UserDataSerializer)
        val contextual = KotlinxSerializer(Json { serializersModule = context })
        val simple = KotlinxSerializer()

        val data = UserData(1, "kotlin")

        val contextualString = contextual.writeContent(data)
        val simpleString = simple.writeContent(data)

        assertEquals("\"1\"\"kotlin\"", contextualString)
        assertEquals("{\"id\":1,\"name\":\"kotlin\"}", simpleString)

        val makeInput = { text: String ->
            buildPacket { writeText(text) }
        }

        assertEquals(data, contextual.read(typeInfo<UserData>(), makeInput(contextualString)))
        assertEquals(data, simple.read(typeInfo<UserData>(), makeInput(simpleString)))
    }
}
