/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import kotlin.collections.mapOf
import kotlin.test.*

class MapApplicationConfigTest {

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
            setOf(
                "auth.hashAlgorithm",
                "auth.salt",
                "auth.users",
                "auth.values",
                "auth.listValues",
                "auth.data.value1",
                "auth.data.value2"
            ),
            keys
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
        mapConfig.put("auth.data1.value1", "other value")

        val nestedConfig = mapConfig.config("auth.nested")
        val keys = nestedConfig.keys()
        assertEquals(setOf("data.value1", "data.value2", "list"), keys)
        assertEquals("1", nestedConfig.property("data.value1").getString())
        assertEquals("2", nestedConfig.property("data.value2").getString())
        assertEquals(listOf("a", "b"), nestedConfig.property("list").getList())
    }

    @Test
    fun testToMap() {
        val config = MapApplicationConfig()
        config.put("hashAlgorithm", "SHA-256")
        config.put("salt", "ktor")
        config.put("values", listOf("a", "b"))
        config.put("listValues", listOf("a", "b", "c"))
        config.put("data.value1", "1")
        config.put("data.value2", "2")
        config.put("users.size", "2")
        config.put("users.0.name", "test")
        config.put("users.0.password", "asd")
        config.put("users.1.name", "other")
        config.put("users.1.password", "qwe")
        config.put("nested.config.value", "a1")

        val map = config.toMap()

        assertEquals(7, map.size)
        assertEquals("SHA-256", map["hashAlgorithm"])
        assertEquals("ktor", map["salt"])
        assertEquals(
            listOf(mapOf("name" to "test", "password" to "asd"), mapOf("name" to "other", "password" to "qwe")),
            map["users"]
        )
        assertEquals(listOf("a", "b"), map["values"])
        assertEquals(listOf("a", "b", "c"), map["listValues"])
        assertEquals(mapOf("value1" to "1", "value2" to "2"), map["data"])

        @Suppress("UNCHECKED_CAST")
        assertEquals("a1", (map["nested"] as Map<String, Map<String, String>>)["config"]!!["value"])
    }

    @Test
    fun testKeys() {
        val config = MapApplicationConfig()
        config.put("simple", "123")
        config.put("foo.plain", "value")
        config.put("foo.complex.some", "value1")
        config.put("foo.complex.some2", "value2")

        config.put("list.size", "2")
        config.put("list.0", "1")
        config.put("list.1", "2")

        config.put("str-list", listOf("a", "b", "c"))

        config.put("broken-list.size", "5")

        assertEquals(
            setOf(
                "simple",
                "foo.plain",
                "foo.complex.some",
                "foo.complex.some2",
                "list",
                "str-list",
                "broken-list.size"
            ),
            config.keys(),
        )
    }

    @Test
    fun testComplexPathList() {
        val config = MapApplicationConfig()
        config.put("foo.bar", listOf("a", "b", "c"))
        val map = config.toMap()
        val list = (map["foo"] as Map<*, *>)["bar"]
        assertIs<List<String>>(list)
        assertContentEquals(listOf("a", "b", "c"), list)
    }

    @Test
    fun testToMapOfLists() {
        val config = MapApplicationConfig()
        config.put("str-list", listOf("a", "b", "c"))

        config.put("list.size", "2")
        config.put("list.0", "1")
        config.put("list.1", "2")

        config.put("obj-list.size", "1")
        config.put("obj-list.0.k1", "v1")
        config.put("obj-list.0.k2", "v2")

        config.put("list-list.size", "2")
        config.put("list-list.0", listOf("a", "b"))
        config.put("list-list.1", listOf("c", "d"))

        assertEquals(
            mapOf(
                "str-list" to listOf("a", "b", "c"),
                "list" to listOf("1", "2"),
                "obj-list" to listOf(mapOf("k1" to "v1", "k2" to "v2")),
                "list-list" to listOf(listOf("a", "b"), listOf("c", "d")),
            ),
            config.toMap()
        )
    }

    @Test
    fun testFromMapToMap() {
        val config = MapApplicationConfig(
            listOf(
                "simple" to "plain",

                "str-list.size" to "2",
                "str-list.0" to "a",
                "str-list.1" to "b",

                "list.size" to "2",
                "list.0.key" to "v3",
                "list.1.key" to "v4",

                "broken-list.size" to "3",

                "obj.key1" to "v1",
                "obj.key2" to "v2",
            ),
        )

        assertEquals(
            mapOf(
                "simple" to "plain",
                "str-list" to listOf("a", "b"),
                "list" to listOf(mapOf("key" to "v3"), mapOf("key" to "v4")),
                "obj" to mapOf("key1" to "v1", "key2" to "v2"),
                "broken-list" to mapOf("size" to "3"),
            ),
            config.toMap()
        )
    }

    @Test
    fun testBrokenConfigList() {
        val config = MapApplicationConfig(
            listOf(
                "broken-list.size" to "4",
                "broken-list.2.key" to "value",
                "broken-list.1" to "plain",

                "simple" to "plain",
                "obj.key" to "value",
            ),
        )

        val subConfigs: Iterator<ApplicationConfig> = config.configList("broken-list").iterator()
        assertEquals(mapOf(), subConfigs.next().toMap())
        assertEquals(mapOf(), subConfigs.next().toMap())
        assertEquals(mapOf("key" to "value"), subConfigs.next().toMap())
        assertEquals(mapOf(), subConfigs.next().toMap())

        assertFailsWith<ApplicationConfigurationException> {
            config.configList("nonexistent")
        }.let {
            assertEquals("Property at \"nonexistent\" not found.", it.message)
        }

        assertFailsWith<ApplicationConfigurationException> {
            config.configList("simple")
        }.let {
            assertEquals("Expected a list of configs at \"simple\", got string value", it.message)
        }

        assertFailsWith<ApplicationConfigurationException> {
            config.configList("obj")
        }.let {
            assertEquals("Expected a list of configs at \"obj\", got config object", it.message)
        }
    }

    @Test
    fun testConfigValues() {
        val config = MapApplicationConfig(
            listOf(
                "simple" to "plain",

                "str-list.size" to "2",
                "str-list.0" to "a",
                "str-list.1" to "b",

                "list.size" to "2",
                "list.0.key" to "v3",
                "list.1.key" to "v4",

                "broken-list.size" to "3",

                "obj.key1" to "v1",
                "obj.key2" to "v2",
            ),
        )

        assertFailsWith<ApplicationConfigurationException> {
            config.property("obj").getString()
        }.let {
            assertEquals("Expected string value at \"obj\", got config object", it.message)
        }

        assertFailsWith<ApplicationConfigurationException> {
            config.property("obj").getList()
        }.let {
            assertEquals("Expected list of string values at \"obj\", got config object", it.message)
        }

        assertEquals(mapOf(), config.property("simple").getMap())
    }

    @Test
    fun testGetList() {
        val config = MapApplicationConfig()
        config.put("str-list", listOf("a", "b", "c"))

        config.put("list.size", "2")
        config.put("list.0", "a")
        config.put("list.1", "b")

        config.put("mixed.size", "2")
        config.put("mixed.0", "a")
        config.put("mixed.1.key", "value")

        config.put("broken-list.size", "3")

        assertEquals(listOf("a", "b", "c"), config.property("str-list").getList())
        assertEquals(listOf("a", "b"), config.property("list").getList())
        assertFailsWith<ApplicationConfigurationException> {
            config.property("broken-list").getList()
        }.let {
            assertEquals("Expected list of string values at \"broken-list\", got config object", it.message)
        }
        assertFailsWith<ApplicationConfigurationException> {
            config.property("mixed").getList()
        }.let {
            assertEquals("Expected list of string values at \"mixed\", got list of config objects", it.message)
        }
    }

    @Test
    fun testOverride() {
        val config = MapApplicationConfig()

        config.put("str", "test1")
        config.put("str", "test2")
        assertEquals("test2", config.property("str").getString())

        config.put("obj.k1", "v1")
        config.put("obj.k2", "v2")
        config.put("obj", "plain")
        assertEquals("plain", config.property("obj").getString())

        config.put("list.size", "1")
        config.put("list.0", "0")
        config.put("list", "plain")
        assertEquals("plain", config.property("list").getString())

        config.put("str-list", listOf("1", "2", "3"))
        config.put("str-list", "plain")
        assertEquals("plain", config.property("str-list").getString())

        config.put("obj2.k1", "v1")
        config.put("obj2.k2", "v2")
        config.put("obj2", "plain3")
        config.put("obj2", listOf("4", "5", "6"))

        assertEquals(listOf("4", "5", "6"), config.property("obj2").getList())
        assertFailsWith<ApplicationConfigurationException> {
            config.property("obj2").getString() // Previous behavior returned plain3
        }
    }
}
