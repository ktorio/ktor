/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.config

import com.typesafe.config.*
import io.ktor.server.config.*
import kotlinx.serialization.Serializable
import kotlin.test.*

class HoconDecoderTest {

    @Test
    fun `primitive types`() {
        val content = """
            test {
                boolean = true
                byte = 127
                short = 32767
                int = 2147483647
                long = 9223372036854775807
                float = 3.14
                double = 2.71828
                char = "X"
                string = "Hello World"
            }
        """.trimIndent()

        val primitiveTypes = parseConfig(content)
            .propertyOrNull("test")?.getAs<PrimitiveTypes>()

        assertNotNull(primitiveTypes)
        assertEquals(true, primitiveTypes.boolean)
        assertEquals(127.toByte(), primitiveTypes.byte)
        assertEquals(32767.toShort(), primitiveTypes.short)
        assertEquals(2147483647, primitiveTypes.int)
        assertEquals(9223372036854775807L, primitiveTypes.long)
        assertEquals(3.14f, primitiveTypes.float)
        assertEquals(2.71828, primitiveTypes.double)
        assertEquals('X', primitiveTypes.char)
        assertEquals("Hello World", primitiveTypes.string)
    }

    @Test
    fun `nullable values`() {
        val content = """
            test {
                nullableString = "Not null"
                # nullableInt is intentionally omitted to test null handling
            }
        """.trimIndent()

        val nullableTypes = parseConfig(content)
            .propertyOrNull("test")?.getAs<NullableTypes>()

        assertNotNull(nullableTypes)
        assertEquals("Not null", nullableTypes.nullableString)
        assertNull(nullableTypes.nullableInt)
    }

    @Test
    fun `maps and type parameters`() {
        val content = """
            stringMap {
                key1 = "value1"
                key2 = "value2"
            }
            stringIntMap {
                key1 = 1
                key2 = 2
            }
        """.trimIndent()

        val config = parseConfig(content)
        assertEquals(
            mapOf("key1" to "value1", "key2" to "value2"),
            config.propertyOrNull("stringMap")?.getAs<Map<String, String>>(),
        )
        assertEquals(
            mapOf("key1" to 1, "key2" to 2),
            config.propertyOrNull("stringIntMap")?.getAs<Map<String, Int>>(),
        )
    }

    @Test
    fun `collection types`() {
        val content = """
            test {
                stringList = ["one", "two", "three"]
                intList = [1, 2, 3]
                stringMap {
                    key1 = "value1"
                    key2 = "value2"
                }
                nestedList = [
                    { id = 1, name = "first" },
                    { id = 2, name = "second" }
                ]
            }
        """.trimIndent()

        val collectionTypes = parseConfig(content)
            .propertyOrNull("test")?.getAs<CollectionTypes>()

        assertNotNull(collectionTypes)
        assertEquals(listOf("one", "two", "three"), collectionTypes.stringList)
        assertEquals(listOf(1, 2, 3), collectionTypes.intList)
        assertEquals(mapOf("key1" to "value1", "key2" to "value2"), collectionTypes.stringMap)
        assertEquals(
            listOf(
                NestedItem(1, "first"),
                NestedItem(2, "second")
            ),
            collectionTypes.nestedList
        )
        assertEquals(
            listOf(1L, 2L),
            parseConfig("longList = [1, 2]")
                .propertyOrNull("longList")?.getAs<List<Long>>()
        )
        val big2 = Integer.MAX_VALUE.toLong() + 2L
        assertEquals(
            listOf(1.0, big2.toDouble(), 3.0e2),
            parseConfig("doubleList = [1, $big2, 3.0e2]")
                .propertyOrNull("doubleList")?.getAs<List<Double>>()
        )
        assertEquals(
            listOf(listOf(1)),
            parseConfig("nestedList = [[1]]")
                .propertyOrNull("nestedList")?.getAs<List<List<Int>>>()
        )
    }

    @Test
    fun `nested structures`() {
        val content = """
            test {
                nested {
                    value = "nested value"
                    number = 42
                }
                nestedList = [
                    {
                        value = "first"
                        inner {
                            id = 1
                        }
                    },
                    {
                        value = "second"
                        inner {
                            id = 2
                        }
                    }
                ]
            }
        """.trimIndent()

        val nestedStructure = parseConfig(content)
            .propertyOrNull("test")?.getAs<NestedStructure>()

        assertNotNull(nestedStructure)
        assertEquals("nested value", nestedStructure.nested.value)
        assertEquals(42, nestedStructure.nested.number)
        assertEquals(2, nestedStructure.nestedList.size)
        assertEquals("first", nestedStructure.nestedList[0].value)
        assertEquals(1, nestedStructure.nestedList[0].inner.id)
        assertEquals("second", nestedStructure.nestedList[1].value)
        assertEquals(2, nestedStructure.nestedList[1].inner.id)
    }

    @Test
    fun `enum values`() {
        val content = """
            test {
                enumValue = "VALUE_TWO"
                enumList = ["VALUE_ONE", "VALUE_THREE"]
            }
        """.trimIndent()

        val enumContainer = parseConfig(content)
            .propertyOrNull("test")?.getAs<EnumContainer>()

        assertNotNull(enumContainer)
        assertEquals(TestEnum.VALUE_TWO, enumContainer.enumValue)
        assertEquals(listOf(TestEnum.VALUE_ONE, TestEnum.VALUE_THREE), enumContainer.enumList)
    }

    @Test
    fun testDecodeMissingValues() {
        val content = """
            test {
                # Empty configuration to test missing values
            }
        """.trimIndent()

        val exception = assertFails {
            parseConfig(content)
                .propertyOrNull("test")?.getAs<PrimitiveTypes>()
        }

        assertTrue(
            exception.message?.contains("no setting") == true ||
                exception.message?.contains("missing") == true
        )
    }

    @Test
    fun `invalid types`() {
        val content = """
            test {
                int = "not an integer"
            }
        """.trimIndent()

        assertFailsWith<ConfigException.WrongType> {
            parseConfig(content)
                .propertyOrNull("test")?.getAs<SimpleInt>()
        }
    }

    @Test
    fun `default values`() {
        val content = """
            address {
                streetName = "Main Street"
                postalCode = "12345"
                municipality = "Test City"
                # unitNumber is intentionally omitted to test default value
            }
        """.trimIndent()

        val address = parseConfig(content)
            .propertyOrNull("address")?.getAs<Address>()

        assertNotNull(address)
        assertEquals("Main Street", address.streetName)
        assertEquals("Test City", address.municipality)
        assertNull(address.unitNumber)
    }

    private fun parseConfig(content: String): HoconApplicationConfig =
        HoconApplicationConfig(ConfigFactory.parseString(content))

    @Serializable
    data class PrimitiveTypes(
        val boolean: Boolean,
        val byte: Byte,
        val short: Short,
        val int: Int,
        val long: Long,
        val float: Float,
        val double: Double,
        val char: Char,
        val string: String
    )

    @Serializable
    data class NullableTypes(
        val nullableString: String? = null,
        val nullableInt: Int? = null
    )

    @Serializable
    data class SimpleInt(val int: Int)

    @Serializable
    data class CollectionTypes(
        val stringList: List<String>,
        val intList: List<Int>,
        val stringMap: Map<String, String>,
        val nestedList: List<NestedItem>
    )

    @Serializable
    data class NestedItem(
        val id: Int,
        val name: String
    )

    @Serializable
    data class NestedStructure(
        val nested: NestedObject,
        val nestedList: List<ComplexNestedItem>
    )

    @Serializable
    data class NestedObject(
        val value: String,
        val number: Int
    )

    @Serializable
    data class ComplexNestedItem(
        val value: String,
        val inner: InnerObject
    )

    @Serializable
    data class InnerObject(
        val id: Int
    )

    @Serializable
    enum class TestEnum {
        VALUE_ONE,
        VALUE_TWO,
        VALUE_THREE
    }

    @Serializable
    data class EnumContainer(
        val enumValue: TestEnum,
        val enumList: List<TestEnum>
    )

    @Serializable
    data class Address(
        val streetName: String,
        val postalCode: String,
        val unitNumber: String? = null,
        val municipality: String,
    )
}
