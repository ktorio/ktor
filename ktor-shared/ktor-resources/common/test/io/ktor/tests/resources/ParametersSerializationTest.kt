/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.resources

import io.ktor.http.*
import io.ktor.resources.serialization.*
import kotlinx.serialization.*
import kotlin.test.*

class ParametersSerializationTest {

    private val resourcesFormat = ResourcesFormat()

    @Serializable
    data class PrimitivesLocations(
        @SerialName("intValue")
        val intValueWrongName: Int,
        val floatValue: Float,
        val charValue: Char,
        val stringValue: String
    )

    @Test
    fun testSimpleSerialization() {
        val primitivesLocation = PrimitivesLocations(1, 2.0F, 'a', "value")
        val encoded = resourcesFormat.encodeToParameters(PrimitivesLocations.serializer(), primitivesLocation)
        val decoded = resourcesFormat.decodeFromParameters(PrimitivesLocations.serializer(), encoded)

        assertEquals(primitivesLocation, decoded)
        assertEquals("1", encoded["intValue"])
        assertEquals(2.0F, encoded["floatValue"]!!.toFloat())
        assertEquals("a", encoded["charValue"])
        assertEquals("value", encoded["stringValue"])
    }

    @Serializable
    data class NestedLocations(
        val someValue: Int,
        val child: PrimitivesLocations
    )

    @Test
    fun testNestedSerialization() {
        val primitivesLocation = PrimitivesLocations(1, 2.0F, 'a', "value")
        val nestedLocations = NestedLocations(2, primitivesLocation)
        val encoded = resourcesFormat.encodeToParameters(NestedLocations.serializer(), nestedLocations)
        val decoded = resourcesFormat.decodeFromParameters(NestedLocations.serializer(), encoded)

        assertEquals(nestedLocations, decoded)
        assertEquals("2", encoded["someValue"])
        assertEquals("1", encoded["intValue"])
        assertEquals(2.0F, encoded["floatValue"]!!.toFloat())
        assertEquals("a", encoded["charValue"])
        assertEquals("value", encoded["stringValue"])
    }

    @Serializable
    data class CollectionLocations(
        val someValues: List<Double>,
        val child: PrimitivesLocations,
        val someMoreValues: List<Int>,
        val empty: List<Float>
    )

    @Test
    fun testCollectionsSerialization() {
        val primitivesLocation = PrimitivesLocations(1, 2.0F, 'a', "value1")
        val collectionLocations =
            CollectionLocations(listOf(3.1, 5.1), primitivesLocation, listOf(3, 5, 7, 9), emptyList())
        val encoded = resourcesFormat.encodeToParameters(CollectionLocations.serializer(), collectionLocations)
        val decoded = resourcesFormat.decodeFromParameters(CollectionLocations.serializer(), encoded)

        assertEquals(collectionLocations, decoded)

        assertEquals(listOf("3.1", "5.1"), encoded.getAll("someValues"))
        assertEquals(listOf("3", "5", "7", "9"), encoded.getAll("someMoreValues"))
        assertEquals("1", encoded["intValue"])
        assertEquals(2.0F, encoded["floatValue"]!!.toFloat())
        assertEquals("a", encoded["charValue"])
        assertEquals(null, encoded["empty"])
        assertEquals("value1", encoded["stringValue"])
    }

    @Serializable
    data class NullableAndDefaultValueLocations(
        val someValue: String? = null,
        val someMoreValue1: Boolean = false,
        val someMoreValue2: Boolean = true,
        val someMoreValue3: Short = 3,
        val someMoreValueNullable: Boolean? = null
    )

    @Test
    fun testNullableAndDefaultValueSerialization() {
        val nullableLocation = NullableAndDefaultValueLocations(null)
        val encoded = resourcesFormat
            .encodeToParameters(NullableAndDefaultValueLocations.serializer(), nullableLocation)
        val decoded = resourcesFormat.decodeFromParameters(NullableAndDefaultValueLocations.serializer(), encoded)

        assertEquals(nullableLocation, decoded)

        val parameters = parametersOf("someValue", "value")
        val decodedFromParameters = resourcesFormat
            .decodeFromParameters(NullableAndDefaultValueLocations.serializer(), parameters)
        assertEquals(decodedFromParameters, NullableAndDefaultValueLocations("value", false, true, 3, null))
    }

    enum class TestEnum { Ab, Cd }

    @Serializable
    data class EnumValueLocations(
        val enum: TestEnum,
        val enumDefault: TestEnum = TestEnum.Cd,
        val enumNullable: TestEnum? = null
    )

    @Test
    fun testEnumValueSerialization() {
        val enumLocation = EnumValueLocations(TestEnum.Ab)
        val encoded = resourcesFormat
            .encodeToParameters(EnumValueLocations.serializer(), enumLocation)
        val decoded = resourcesFormat.decodeFromParameters(EnumValueLocations.serializer(), encoded)

        val parameters = parametersOf("enum", "Ab")
        val decodedFromParameters = resourcesFormat.decodeFromParameters(EnumValueLocations.serializer(), parameters)

        assertEquals(enumLocation, decoded)
        assertEquals(enumLocation, decodedFromParameters)
        assertEquals("Ab", encoded["enum"])
        assertEquals("Cd", encoded["enumDefault"])
        assertEquals(null, encoded["enumNullable"])
    }

    @Serializable
    data class NestedDefaultValueLocations(
        val someValue: String,
        val child: PrimitivesLocations = PrimitivesLocations(1, 2.0F, 'a', "value1"),
        val childNullable: PrimitivesLocations? = null
    )

    @Test
    fun testNestedDefaultValueSerialization() {
        val nestedDefaultValueLocations = NestedDefaultValueLocations("2")
        val encoded = resourcesFormat
            .encodeToParameters(NestedDefaultValueLocations.serializer(), nestedDefaultValueLocations)
        val decoded = resourcesFormat.decodeFromParameters(NestedDefaultValueLocations.serializer(), encoded)

        assertEquals(nestedDefaultValueLocations, decoded)
        assertEquals("2", encoded["someValue"])
        assertEquals("1", encoded["intValue"])
        assertEquals(2.0F, encoded["floatValue"]!!.toFloat())
        assertEquals("a", encoded["charValue"])
        assertEquals("value1", encoded["stringValue"])
    }

    @Serializable
    data class NestedDefaultLocations(
        val someValue: Int,
        val child: PrimitivesLocations = PrimitivesLocations(1, 2.0F, 'c', "value")
    )

    @Test
    fun testNestedDefaultSerialization() {
        val nestedLocations = NestedDefaultLocations(2)
        val encoded = resourcesFormat.encodeToParameters(NestedDefaultLocations.serializer(), nestedLocations)
        val decoded = resourcesFormat.decodeFromParameters(NestedDefaultLocations.serializer(), encoded)

        assertEquals(nestedLocations, decoded)
        assertEquals("2", encoded["someValue"])
        assertEquals("1", encoded["intValue"])
        assertEquals(2.0F, encoded["floatValue"]!!.toFloat())
        assertEquals("c", encoded["charValue"])
        assertEquals("value", encoded["stringValue"])
    }
}
