/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import io.ktor.server.config.MapDecoderTest.Address
import io.ktor.server.config.MapDecoderTest.EnumConfig
import io.ktor.server.config.MapDecoderTest.ListConfig
import io.ktor.server.config.MapDecoderTest.MapConfig
import io.ktor.server.config.MapDecoderTest.SimpleConfig
import io.ktor.server.config.MapDecoderTest.TestEnum
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MapConfigDecodeTest {

    @Test
    fun testSimpleTypes() {
        val config = MapApplicationConfig()
        config.put("x.string", "test")
        config.put("x.int", "42")
        config.put("x.long", "1234567890")
        config.put("x.float", "3.14")
        config.put("x.double", "3.14159")
        config.put("x.boolean", "true")
        config.put("x.char", "x")
        config.put("x.byte", "127")
        config.put("x.short", "32000")

        assertEquals("test", config.property("x.string").getAs())
        assertEquals(42, config.property("x.int").getAs())
        assertEquals(1234567890, config.property("x.long").getAs())
        assertEquals(3.14f, config.property("x.float").getAs())
        assertEquals(3.14159, config.property("x.double").getAs())
        assertTrue(config.property("x.boolean").getAs())
        assertEquals('x', config.property("x.char").getAs())
        assertEquals(127.toByte(), config.property("x.byte").getAs())
        assertEquals(32000.toShort(), config.property("x.short").getAs())

        val x = config.property("x").getAs<SimpleConfig>()
        assertEquals("test", x.string)
        assertEquals(42, x.int)
        assertEquals(1234567890, x.long)
        assertEquals(3.14f, x.float)
        assertEquals(3.14159, x.double)
        assertTrue(x.boolean)
        assertEquals('x', x.char)
        assertEquals(127.toByte(), x.byte)
        assertEquals(32000.toShort(), x.short)
    }

    @Test
    fun testLists() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("x.strings.size", "3")
        mapConfig.put("x.strings.0", "one")
        mapConfig.put("x.strings.1", "two")
        mapConfig.put("x.strings.2", "three")
        mapConfig.put("x.ints.size", "2")
        mapConfig.put("x.ints.0", "1")
        mapConfig.put("x.ints.1", "2")
        mapConfig.put("x.nested.size", "2")
        mapConfig.put("x.nested.0.string", "first")
        mapConfig.put("x.nested.0.int", "1")
        mapConfig.put("x.nested.0.long", "1")
        mapConfig.put("x.nested.0.float", "1.0")
        mapConfig.put("x.nested.0.double", "1.0")
        mapConfig.put("x.nested.0.boolean", "true")
        mapConfig.put("x.nested.0.char", "a")
        mapConfig.put("x.nested.0.byte", "1")
        mapConfig.put("x.nested.0.short", "1")
        mapConfig.put("x.nested.1.string", "second")
        mapConfig.put("x.nested.1.int", "2")
        mapConfig.put("x.nested.1.long", "2")
        mapConfig.put("x.nested.1.float", "2.0")
        mapConfig.put("x.nested.1.double", "2.0")
        mapConfig.put("x.nested.1.boolean", "false")
        mapConfig.put("x.nested.1.char", "b")
        mapConfig.put("x.nested.1.byte", "2")
        mapConfig.put("x.nested.1.short", "2")

        // TODO: Direct getAs

        val config = mapConfig.property("x").getAs<ListConfig>()

        assertEquals(listOf("one", "two", "three"), config.strings)
        assertEquals(listOf(1, 2), config.ints)
        assertEquals(2, config.nested.size)
        assertEquals("first", config.nested[0].string)
        assertEquals("second", config.nested[1].string)
    }

    @Test
    fun testMaps() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("x.stringMap.first", "one")
        mapConfig.put("x.stringMap.second", "two")
        mapConfig.put("x.intMap.first", "1")
        mapConfig.put("x.intMap.second", "2")
        mapConfig.put("x.configMap.first.string", "test1")
        mapConfig.put("x.configMap.first.int", "1")
        mapConfig.put("x.configMap.first.long", "1")
        mapConfig.put("x.configMap.first.float", "1.0")
        mapConfig.put("x.configMap.first.double", "1.0")
        mapConfig.put("x.configMap.first.boolean", "true")
        mapConfig.put("x.configMap.first.char", "a")
        mapConfig.put("x.configMap.first.byte", "1")
        mapConfig.put("x.configMap.first.short", "1")
        mapConfig.put("x.configMap.second.string", "test2")
        mapConfig.put("x.configMap.second.int", "2")
        mapConfig.put("x.configMap.second.long", "2")
        mapConfig.put("x.configMap.second.float", "2.0")
        mapConfig.put("x.configMap.second.double", "2.0")
        mapConfig.put("x.configMap.second.boolean", "false")
        mapConfig.put("x.configMap.second.char", "b")
        mapConfig.put("x.configMap.second.byte", "2")
        mapConfig.put("x.configMap.second.short", "2")

        // TODO: Direct getAs

        val config = mapConfig.property("x").getAs<MapConfig>()
        assertEquals(mapOf("first" to "one", "second" to "two"), config.stringMap)
        assertEquals(mapOf("first" to 1, "second" to 2), config.intMap)
        assertEquals(2, config.configMap.size)
        assertEquals("test1", config.configMap["first"]?.string)
        assertEquals("test2", config.configMap["second"]?.string)
    }

    @Test
    fun testEnums() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("x.enum", "TWO")
        mapConfig.put("x.enumList.size", "3")
        mapConfig.put("x.enumList.0", "ONE")
        mapConfig.put("x.enumList.1", "TWO")
        mapConfig.put("x.enumList.2", "THREE")
        mapConfig.put("x.enumMap.first", "ONE")
        mapConfig.put("x.enumMap.second", "TWO")

        assertEquals(TestEnum.TWO, mapConfig.property("x.enum").getAs())
        assertEquals(listOf(TestEnum.ONE, TestEnum.TWO, TestEnum.THREE), mapConfig.property("x.enumList").getAs())
        assertEquals(
            mapOf("first" to TestEnum.ONE, "second" to TestEnum.TWO),
            mapConfig.property("x.enumMap").getAs()
        )

        val config = mapConfig.property("x").getAs<EnumConfig>()
        assertEquals(TestEnum.TWO, config.enum)
        assertEquals(listOf(TestEnum.ONE, TestEnum.TWO, TestEnum.THREE), config.enumList)
        assertEquals(mapOf("first" to TestEnum.ONE, "second" to TestEnum.TWO), config.enumMap)
    }

    @Test
    fun testMissingValue() {
        assertFailsWith<SerializationException> {
            MapApplicationConfig().apply {
                put("x.key", "value")
            }.property("x").getAs<SimpleConfig>()
        }
    }

    @Test
    fun testInvalidNumber() {
        val config = MapApplicationConfig()
        config.put("x.string", "test")
        config.put("x.int", "not a number")
        config.put("x.long", "1")
        config.put("x.float", "1.0")
        config.put("x.double", "1.0")
        config.put("x.boolean", "true")
        config.put("x.char", "x")
        config.put("x.byte", "1")
        config.put("x.short", "1")

        assertFailsWith<NumberFormatException> {
            config.property("x").getAs<SimpleConfig>()
        }
    }

    @Test
    fun testOptionals() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("x.streetName", "Test street")
        mapConfig.put("x.postalCode","12345")
        mapConfig.put("x.municipality", "Test municipality")

        val config = mapConfig.property("x").getAs<Address>()
        assertEquals("Test street", config.streetName)
        assertEquals("12345", config.postalCode)
        assertEquals("Test municipality", config.municipality)
        assertEquals(null, config.unitNumber)
    }
}
