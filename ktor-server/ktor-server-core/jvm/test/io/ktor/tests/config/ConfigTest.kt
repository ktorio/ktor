/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.config

import com.typesafe.config.*
import io.ktor.server.config.*
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

        assertEquals(listOf("a", "b", "c"), auth.property("listValues").getList())

        val values = auth.property("values").getList()
        assertEquals("[a, b]", values.toString())

        assertEquals(null, auth.propertyOrNull("missingProperty"))
        assertEquals("SHA-256", auth.propertyOrNull("hashAlgorithm")?.getString())
        assertEquals(listOf("a", "b", "c"), auth.propertyOrNull("listValues")?.getList())

        assertEquals(null, mapConfig.propertyOrNull("missingProperty"))
        assertEquals(null, mapConfig.propertyOrNull("auth.missingProperty"))
        assertEquals("SHA-256", mapConfig.propertyOrNull("auth.hashAlgorithm")?.getString())
        assertEquals(listOf("a", "b", "c"), mapConfig.propertyOrNull("auth.listValues")?.getList())
    }

    @Test
    fun testMapApplicationConfigNestedPut() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("auth.salt", "ktor")

        val nested = mapConfig.config("auth") as MapApplicationConfig
        nested.put("value1", "data1")

        assertEquals("ktor", mapConfig.property("auth.salt").getString())
        assertEquals("data1", mapConfig.property("auth.value1").getString())
        assertEquals("data1", nested.property("value1").getString())
    }

    @Test
    fun testKeysTopLevelMapConfig() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("auth.hashAlgorithm", "SHA-256")
        mapConfig.put("auth.salt", "ktor")
        mapConfig.put("auth.users.size", "1")
        mapConfig.put("auth.users.0.name", "test")

        mapConfig.put("auth.values.size", "2")
        mapConfig.put("auth.values.0", "a")
        mapConfig.put("auth.values.1", "b")

        mapConfig.put("auth.listValues", listOf("a", "b", "c"))

        mapConfig.put("auth.data.value1", "1")
        mapConfig.put("auth.data.value2", "2")

        val keys = mapConfig.keys()
        assertEquals(
            keys,
            setOf(
                "auth.hashAlgorithm",
                "auth.salt",
                "auth.users",
                "auth.values",
                "auth.listValues",
                "auth.data.value1",
                "auth.data.value2"
            )
        )
    }

    @Test
    fun testKeysNestedMapConfig() {
        val mapConfig = MapApplicationConfig()
        mapConfig.put("auth.nested.data.value1", "1")
        mapConfig.put("auth.nested.data.value2", "2")
        mapConfig.put("auth.nested.list.size", "2")
        mapConfig.put("auth.nested.list.0", "a")
        mapConfig.put("auth.nested.list.1", "b")
        mapConfig.put("auth.data1.value1", "1")

        val nestedConfig = mapConfig.config("auth.nested")
        val keys = nestedConfig.keys()
        assertEquals(keys, setOf("data.value1", "data.value2", "list"))
        assertEquals("1", nestedConfig.property("data.value1").getString())
        assertEquals("2", nestedConfig.property("data.value2").getString())
        assertEquals(listOf("a", "b"), nestedConfig.property("list").getList())
    }

    @Test
    fun testKeysTopLevelHoconConfig() {
        val mapConfig = mutableMapOf<String, Any>()
        mapConfig["auth.hashAlgorithm"] = "SHA-256"
        mapConfig["auth.salt"] = "ktor"
        mapConfig["auth.users"] = listOf(mapOf("name" to "test"))

        mapConfig["auth.values"] = listOf("a", "b")
        mapConfig["auth.listValues"] = listOf("a", "b", "c")

        mapConfig["auth.data"] = mapOf("value1" to "1", "value2" to "2")

        val config = HoconApplicationConfig(ConfigFactory.parseMap(mapConfig))
        val keys = config.keys()
        assertEquals(
            keys,
            setOf(
                "auth.hashAlgorithm",
                "auth.salt",
                "auth.users",
                "auth.values",
                "auth.listValues",
                "auth.data.value1",
                "auth.data.value2"
            )
        )
    }

    @Test
    fun testKeysNestedHoconConfig() {
        val mapConfig = mutableMapOf<String, Any>()
        mapConfig["auth.nested.data"] = mapOf("value1" to "1", "value2" to "2")
        mapConfig["auth.nested.list"] = listOf("a", "b")
        mapConfig["auth.data1.value1"] = "1"

        val config = HoconApplicationConfig(ConfigFactory.parseMap(mapConfig))
        val nestedConfig = config.config("auth.nested")
        val keys = nestedConfig.keys()
        assertEquals(keys, setOf("data.value1", "data.value2", "list"))
        assertEquals("1", nestedConfig.property("data.value1").getString())
        assertEquals("2", nestedConfig.property("data.value2").getString())
        assertEquals(listOf("a", "b"), nestedConfig.property("list").getList())
    }
}
