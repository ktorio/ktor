/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx.test.json

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.serialization.kotlinx.test.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import kotlin.test.*

class JsonSerializationTest : AbstractSerializationTest<Json>() {
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val defaultSerializationFormat: Json = DefaultJson

    override fun assertEquals(expectedAsJson: String, actual: ByteArray, format: Json): Boolean {
        return expectedAsJson == actual.decodeToString()
    }

    @Test
    fun testJsonElements() = testSuspend {
        val testSerializer = KotlinxSerializationConverter(defaultSerializationFormat)
        testSerializer.testSerialize(
            buildJsonObject {
                put("a", "1")
                put(
                    "b",
                    buildJsonObject {
                        put("c", 3)
                    }
                )
                put("x", JsonNull)
            }
        ).let { result ->
            assertEquals("""{"a":"1","b":{"c":3},"x":null}""", result.decodeToString())
        }

        testSerializer.testSerialize(
            buildJsonObject {
                put("a", "1")
                put(
                    "b",
                    buildJsonArray {
                        add("c")
                        add(JsonPrimitive(2))
                    }
                )
            }
        ).let { result ->
            assertEquals("""{"a":"1","b":["c",2]}""", result.decodeToString())
        }
    }

    @Test
    fun testContextual() = testSuspend {
        val serializer = KotlinxSerializationConverter(
            Json {
                prettyPrint = true
                encodeDefaults = true
                serializersModule =
                    SerializersModule {
                        contextual(Either::class) { serializers: List<KSerializer<*>> ->
                            EitherSerializer(serializers[0], serializers[1])
                        }
                    }
            }
        )
        val dogJson = """{"age": 8,"name":"Auri"}"""
        assertEquals(
            Either.Right(DogDTO(8, "Auri")),
            serializer.deserialize(
                Charsets.UTF_8,
                typeInfo<Either<ErrorDTO, DogDTO>>(),
                ByteReadChannel(dogJson.toByteArray())
            )
        )
        val errorJson = """{"message": "Some error"}"""
        assertEquals(
            Either.Left(ErrorDTO("Some error")),
            serializer.deserialize(
                Charsets.UTF_8,
                typeInfo<Either<ErrorDTO, DogDTO>>(),
                ByteReadChannel(errorJson.toByteArray())
            )
        )

        val emptyErrorJson = "{}"
        assertEquals(
            Either.Left(ErrorDTO("Some default error")),
            serializer.deserialize(
                Charsets.UTF_8,
                typeInfo<Either<ErrorDTO, DogDTO>>(),
                ByteReadChannel(emptyErrorJson.toByteArray())
            )
        )
    }

    @Test
    fun testExtraFields() = testSuspend {
        val testSerializer = KotlinxSerializationConverter(defaultSerializationFormat)
        val dogExtraFieldJson = """{"age": 8,"name":"Auri","color":"Black"}"""
        assertFailsWith<JsonConvertException> {
            testSerializer.deserialize(
                Charsets.UTF_8,
                typeInfo<DogDTO>(),
                ByteReadChannel(dogExtraFieldJson.toByteArray())
            )
        }
    }

    @Test
    fun testList() = testSuspend {
        val testSerializer = KotlinxSerializationConverter(defaultSerializationFormat)
        val dogListJson = """[{"age": 8,"name":"Auri"}]"""
        assertEquals(
            listOf(DogDTO(8, "Auri")),
            testSerializer.deserialize(
                Charsets.UTF_8,
                typeInfo<List<DogDTO>>(),
                ByteReadChannel(dogListJson.toByteArray())
            )
        )
    }

    @Test
    fun testListsWithExperimentApi() = testSuspend {
        val testSerializer = ExperimentalJsonConverter(defaultSerializationFormat)
        val expected = listOf(DogDTO(8, "Auri"))
        val serialized = testSerializer.serialize(
            ContentType.Application.Json,
            Charsets.UTF_8,
            typeInfo<List<DogDTO>>(),
            expected
        ).toByteArray().decodeToString()
        assertEquals("""[{"age":8,"name":"Auri"}]""", serialized)
        assertEquals(
            expected,
            testSerializer.deserialize(
                Charsets.UTF_8,
                typeInfo<List<DogDTO>>(),
                ByteReadChannel(serialized.toByteArray())
            )
        )
    }

    @Test
    fun testSequence() = testSuspend {
        if (!PlatformUtils.IS_JVM) return@testSuspend

        val testSerializer = KotlinxSerializationConverter(defaultSerializationFormat)
        val dogListJson = """[{"age":8,"name":"Auri"}]"""
        assertContentEquals(
            sequenceOf(DogDTO(8, "Auri")),
            testSerializer.deserialize(
                Charsets.UTF_8,
                typeInfo<Sequence<DogDTO>>(),
                ByteReadChannel(dogListJson.toByteArray())
            ) as Sequence<*>
        )
    }
}

@Serializable
data class DogDTO(val age: Int, val name: String)

@Serializable
data class ErrorDTO(val message: String = "Some default error")

sealed class Either<out L, out R> {

    data class Left<out L>(val left: L) : Either<L, Nothing>()

    data class Right<out R>(val right: R) : Either<Nothing, R>()
}

class EitherSerializer<L, R>(
    private val leftSerializer: KSerializer<L>,
    private val rightSerializer: KSerializer<R>,
) : KSerializer<Either<L, R>> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("NetworkEitherSerializer") {
            element("left", leftSerializer.descriptor)
            element("right", rightSerializer.descriptor)
        }

    override fun deserialize(decoder: Decoder): Either<L, R> {
        require(decoder is JsonDecoder) { "only works in JSON format" }
        val element: JsonElement = decoder.decodeJsonElement()

        return try {
            Either.Right(decoder.json.decodeFromJsonElement(rightSerializer, element))
        } catch (throwable: Throwable) {
            Either.Left(decoder.json.decodeFromJsonElement(leftSerializer, element))
        }
    }

    override fun serialize(encoder: Encoder, value: Either<L, R>) {
        when (value) {
            is Either.Left -> encoder.encodeSerializableValue(leftSerializer, value.left)
            is Either.Right -> encoder.encodeSerializableValue(rightSerializer, value.right)
        }
    }
}
