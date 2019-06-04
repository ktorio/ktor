/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.application

import io.ktor.config.*
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ConfigTest {
    @Test
    fun testMapApplicationConfig() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("auth.hashAlgorithm", "SHA-256")
        mapConfig.put("auth.salt", "ktor")
        mapConfig.put("auth.users.size", "1")
        mapConfig.put("auth.users.0.name", "test")

        mapConfig.put("auth.values.size", "2")
        mapConfig.put("auth.values.0", "a")
        mapConfig.put("auth.values.1", "b")

        mapConfig.put("auth.listValues", listOf("a", "b", "c"))

        val auth = mapConfig.config("auth")
        assertEquals("ktor", auth.property("salt").getString())
        val users = auth.configList("users")
        assertEquals(1, users.size)
        assertEquals("test", users[0].property("name").getString())

        assertEquals(listOf("a", "b", "c"), auth.property("listValues").getStringList())

        val values = auth.property("values").getStringList()
        assertEquals("[a, b]", values.toString())

        assertEquals(null, auth.propertyOrNull("missingProperty"))
        assertEquals("SHA-256", auth.propertyOrNull("hashAlgorithm")?.getString())
        assertEquals(listOf("a", "b", "c"), auth.propertyOrNull("listValues")?.getStringList())

        assertEquals(null, mapConfig.propertyOrNull("missingProperty"))
        assertEquals(null, mapConfig.propertyOrNull("auth.missingProperty"))
        assertEquals("SHA-256", mapConfig.propertyOrNull("auth.hashAlgorithm")?.getString())
        assertEquals(listOf("a", "b", "c"), mapConfig.propertyOrNull("auth.listValues")?.getStringList())
    }

    private fun setupConfig(value: Any): HoconApplicationConfigValue {
        val mapConfig = MapApplicationConfig()

        mapConfig.put("config.property", value)

        return mapConfig.config("config").property("property")
    }

    @Test
    fun testGetBoolean() {
        val expected = true

        val result = setupConfig(expected).getBoolean()

        assertEquals(expected, result)
    }

    @Test
    fun testGetNumber() {
        val expected = 123.45

        val result = setupConfig(expected).getNumber()

        assertEquals(expected, result)
    }

    @Test
    fun testGetInt() {
        val expected = 123

        val result = setupConfig(expected).getInt()

        assertEquals(expected, result)
    }

    @Test
    fun testGetLong() {
        val expected = 123L

        val result = setupConfig(expected).getLong()

        assertEquals(expected, result)
    }

    @Test
    fun testGetDouble() {
        val expected = 123.45

        val result = setupConfig(expected).getDouble()

        assertEquals(expected, result)
    }

    @Test
    fun testGetString() {
        val expected = "This is the expected value"

        val result = setupConfig(expected).getString()

        assertEquals(expected, result)
    }

    enum class TestEnum {
        ENUM_VALUE, OTHER_ENUM_VALUE
    }

    @Test
    fun testGetEnum() {
        val result = setupConfig(TestEnum.ENUM_VALUE.name).getEnum(TestEnum::class)

        assertEquals(TestEnum.ENUM_VALUE, result)
    }

    @Test
    fun testGetAnyRef() {
        val expected = "This could be anything"

        val result = setupConfig(expected).getAnyRef()

        assertEquals(expected, result)
    }

    @Test
    fun testGetMemorySize() {
        val result = setupConfig("1KiB").getMemorySize()

        assertEquals(1024, result.toBytes())
    }

    @Test
    fun testGetDurationWithTimeUnit() {
        val result = setupConfig("1m").getDuration(TimeUnit.SECONDS)

        assertEquals(60, result)
    }

    @Test
    fun testGetDuration() {
        val result = setupConfig("10s").getDuration()

        assertEquals(Duration.ofSeconds(10), result)
    }

    @Test
    fun testGetBooleanList() {
        val expected = listOf(false, true)

        val result = setupConfig(expected).getBooleanList()

        assertEquals(expected, result)
    }

    @Test
    fun testGetNumberList() {
        val expected = listOf(123.45, 543.21)

        val result = setupConfig(expected).getNumberList()

        assertEquals(expected, result)
    }

    @Test
    fun testGetIntList() {
        val expected = listOf(123, 321)

        val result = setupConfig(expected).getIntList()

        assertEquals(expected, result)
    }

    @Test
    fun testGetLongList() {
        val expected = listOf(123L, 321L)

        val result = setupConfig(expected).getLongList()

        assertEquals(expected, result)
    }

    @Test
    fun testGetDoubleList() {
        val expected = listOf(123.45, 543.21)

        val result = setupConfig(expected).getDoubleList()

        assertEquals(expected, result)
    }

    @Test
    fun testGetStringList() {
        val expected = listOf("This is the expected value", "This is another expected value")

        val result = setupConfig(expected).getStringList()

        assertEquals(expected, result)
    }

    @Test
    fun testGetEnumList() {
        val expectedEnums = listOf(TestEnum.ENUM_VALUE, TestEnum.OTHER_ENUM_VALUE)

        val result = setupConfig(expectedEnums.map { it.name }).getEnumList(TestEnum::class)

        assertEquals(expectedEnums, result)
    }

    @Test
    fun testGetAnyRefList() {
        val expected = listOf("This could be anything", 123)

        val result = setupConfig(expected).getAnyRefList()

        assertEquals(expected, result)
    }

    @Test
    fun testGetMemorySizeList() {
        val result = setupConfig(listOf("1KiB", "1MiB")).getMemorySizeList()

        assertEquals(listOf(1024L, 1048576L), result.map { it.toBytes() })
    }

    @Test
    fun testGetDurationWithTimeUnitList() {
        val result = setupConfig(listOf("1m", "15s")).getDurationList(TimeUnit.SECONDS)

        assertEquals(listOf(60L, 15L), result)
    }

    @Test
    fun testGetDurationList() {
        val result = setupConfig(listOf("1m", "15s")).getDurationList()

        assertEquals(listOf(Duration.ofMinutes(1), Duration.ofSeconds(15)), result)
    }
}
