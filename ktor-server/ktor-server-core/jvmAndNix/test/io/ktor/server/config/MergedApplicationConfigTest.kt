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
