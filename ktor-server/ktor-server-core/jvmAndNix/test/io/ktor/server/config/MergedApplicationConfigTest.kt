/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import kotlin.test.*

class MergedApplicationConfigTest {

    @Test
    fun testProperty() {
        val first = MapApplicationConfig("first" to "value1")
        val second = MapApplicationConfig("first" to "value2", "second" to "value3")
        val config = MergedApplicationConfig(first, second)
        assertEquals("value1", config.property("first").getString())
        assertEquals("value3", config.property("second").getString())
    }

    @Test
    fun testConfig() {
        val first = MapApplicationConfig("first.second.third1" to "value1", "first.second.third2" to "value2")
        val second = MapApplicationConfig("first.second.third1" to "ignored", "first.second.third3" to "value3")
        val config = MergedApplicationConfig(first, second)
        val mergedNestedConfig = config.config("first.second")
        assertEquals("value1", mergedNestedConfig.property("third1").getString())
        assertEquals("value2", mergedNestedConfig.property("third2").getString())
        assertEquals("value3", mergedNestedConfig.property("third3").getString())
        assertEquals(setOf("third1", "third2", "third3"), mergedNestedConfig.keys())
    }

    @Test
    fun testConfigList() {
        val first = MapApplicationConfig(
            "first.second.size" to "2",
            "first.second.0.third" to "value1",
            "first.second.1.third" to "value2"
        )
        val second = MapApplicationConfig(
            "first.second.size" to "2",
            "first.second.0.third" to "value3",
            "first.second.1.third" to "value4"
        )
        val config = MergedApplicationConfig(first, second)
        val configs = config.configList("first.second")
        assertEquals(4, configs.size)
        assertEquals("value1", configs[0].property("third").getString())
        assertEquals("value2", configs[1].property("third").getString())
        assertEquals("value3", configs[2].property("third").getString())
        assertEquals("value4", configs[3].property("third").getString())
    }

    @Test
    fun testPropertyOrNull() {
        val first = MapApplicationConfig("first" to "value1")
        val second = MapApplicationConfig("first" to "value2", "second" to "value3")
        val config = MergedApplicationConfig(first, second)
        assertEquals("value1", config.property("first").getString())
        assertEquals("value3", config.property("second").getString())
        assertNull(config.propertyOrNull("third"))
    }

    @Test
    fun testKeys() {
        val first = MapApplicationConfig("first" to "value1")
        val second = MapApplicationConfig("first" to "value2", "second" to "value3")
        val config = MergedApplicationConfig(first, second)
        assertEquals(setOf("first", "second"), config.keys())
    }

    @Test
    fun testToMap() {
        val first = MapApplicationConfig("first" to "value1")
        val second = MapApplicationConfig("first" to "value2", "second" to "value3")
        val config = MergedApplicationConfig(first, second)
        assertEquals(mapOf("first" to "value1", "second" to "value3"), config.toMap())
    }
}
