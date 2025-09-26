/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import kotlinx.serialization.*
import kotlin.test.*

@OptIn(ExperimentalSerializationApi::class)
class MapDecoderTest {
    @Serializable
    data class SimpleConfig(
        val string: String,
        val int: Int,
        val long: Long,
        val float: Float,
        val double: Double,
        val boolean: Boolean,
        val char: Char,
        val byte: Byte,
        val short: Short
    )

    @Serializable
    data class ListConfig(
        val strings: List<String>,
        val ints: List<Int>,
        val nested: List<SimpleConfig>
    )

    @Serializable
    data class MapConfig(
        val stringMap: Map<String, String>,
        val intMap: Map<String, Int>,
        val configMap: Map<String, SimpleConfig>
    )

    @Serializable
    enum class TestEnum {
        ONE,
        TWO,
        THREE
    }

    @Serializable
    data class EnumConfig(
        val enum: TestEnum,
        val enumList: List<TestEnum>,
        val enumMap: Map<String, TestEnum>
    )

    @Serializable
    data class Address(
        val streetName: String,
        val postalCode: String,
        val unitNumber: String? = null,
        val municipality: String,
    )

    @Test
    fun testSimpleTypes() {
        val map = mapOf(
            "string" to "test",
            "int" to "42",
            "long" to "1234567890",
            "float" to "3.14",
            "double" to "3.14159",
            "boolean" to "true",
            "char" to "x",
            "byte" to "127",
            "short" to "32000"
        )

        val decoder = MapConfigDecoder(map)
        val config = SimpleConfig.serializer().deserialize(decoder)

        assertEquals("test", config.string)
        assertEquals(42, config.int)
        assertEquals(1234567890, config.long)
        assertEquals(3.14f, config.float)
        assertEquals(3.14159, config.double)
        assertTrue(config.boolean)
        assertEquals('x', config.char)
        assertEquals(127.toByte(), config.byte)
        assertEquals(32000.toShort(), config.short)
    }

    @Test
    fun testLists() {
        val map = mapOf(
            "strings.size" to "3",
            "strings.0" to "one",
            "strings.1" to "two",
            "strings.2" to "three",
            "ints.size" to "2",
            "ints.0" to "1",
            "ints.1" to "2",
            "nested.size" to "2",
            "nested.0.string" to "first",
            "nested.0.int" to "1",
            "nested.0.long" to "1",
            "nested.0.float" to "1.0",
            "nested.0.double" to "1.0",
            "nested.0.boolean" to "true",
            "nested.0.char" to "a",
            "nested.0.byte" to "1",
            "nested.0.short" to "1",
            "nested.1.string" to "second",
            "nested.1.int" to "2",
            "nested.1.long" to "2",
            "nested.1.float" to "2.0",
            "nested.1.double" to "2.0",
            "nested.1.boolean" to "false",
            "nested.1.char" to "b",
            "nested.1.byte" to "2",
            "nested.1.short" to "2"
        )

        val decoder = MapConfigDecoder(map)
        val config = ListConfig.serializer().deserialize(decoder)

        assertEquals(listOf("one", "two", "three"), config.strings)
        assertEquals(listOf(1, 2), config.ints)
        assertEquals(2, config.nested.size)
        assertEquals("first", config.nested[0].string)
        assertEquals("second", config.nested[1].string)
    }

    @Test
    fun testMaps() {
        val map = mapOf(
            "stringMap.first" to "one",
            "stringMap.second" to "two",
            "intMap.first" to "1",
            "intMap.second" to "2",
            "configMap.first.string" to "test1",
            "configMap.first.int" to "1",
            "configMap.first.long" to "1",
            "configMap.first.float" to "1.0",
            "configMap.first.double" to "1.0",
            "configMap.first.boolean" to "true",
            "configMap.first.char" to "a",
            "configMap.first.byte" to "1",
            "configMap.first.short" to "1",
            "configMap.second.string" to "test2",
            "configMap.second.int" to "2",
            "configMap.second.long" to "2",
            "configMap.second.float" to "2.0",
            "configMap.second.double" to "2.0",
            "configMap.second.boolean" to "false",
            "configMap.second.char" to "b",
            "configMap.second.byte" to "2",
            "configMap.second.short" to "2"
        )

        val decoder = MapConfigDecoder(map)
        val config = MapConfig.serializer().deserialize(decoder)

        assertEquals(mapOf("first" to "one", "second" to "two"), config.stringMap)
        assertEquals(mapOf("first" to 1, "second" to 2), config.intMap)
        assertEquals(2, config.configMap.size)
        assertEquals("test1", config.configMap["first"]?.string)
        assertEquals("test2", config.configMap["second"]?.string)
    }

    @Test
    fun testEnums() {
        val map = mapOf(
            "enum" to "TWO",
            "enumList.size" to "3",
            "enumList.0" to "ONE",
            "enumList.1" to "TWO",
            "enumList.2" to "THREE",
            "enumMap.first" to "ONE",
            "enumMap.second" to "TWO",
        )

        val decoder = MapConfigDecoder(map)
        val config = EnumConfig.serializer().deserialize(decoder)

        assertEquals(TestEnum.TWO, config.enum)
        assertEquals(listOf(TestEnum.ONE, TestEnum.TWO, TestEnum.THREE), config.enumList)
        assertEquals(mapOf("first" to TestEnum.ONE, "second" to TestEnum.TWO), config.enumMap)
    }

    @Test
    fun testMissingValue() {
        val map = mapOf<String, String>()
        val decoder = MapConfigDecoder(map)

        assertFailsWith<SerializationException> {
            SimpleConfig.serializer().deserialize(decoder)
        }
    }

    @Test
    fun testInvalidNumber() {
        val map = mapOf(
            "string" to "test",
            "int" to "not a number",
            "long" to "1",
            "float" to "1.0",
            "double" to "1.0",
            "boolean" to "true",
            "char" to "x",
            "byte" to "1",
            "short" to "1"
        )

        val decoder = MapConfigDecoder(map)

        assertFailsWith<NumberFormatException> {
            SimpleConfig.serializer().deserialize(decoder)
        }
    }

    @Test
    fun testOptionals() {
        val map = mapOf(
            "streetName" to "Test street",
            "postalCode" to "12345",
            "municipality" to "Test municipality"
        )
        val decoder = MapConfigDecoder(map)
        val config = Address.serializer().deserialize(decoder)
        assertEquals("Test street", config.streetName)
        assertEquals("12345", config.postalCode)
        assertEquals("Test municipality", config.municipality)
        assertEquals(null, config.unitNumber)
    }
}
