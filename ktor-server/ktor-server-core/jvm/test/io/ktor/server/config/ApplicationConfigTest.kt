/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import io.ktor.server.testing.*
import org.junit.jupiter.api.*
import kotlin.test.*
import kotlin.test.Test

class ApplicationConfigTest {
    @Test
    fun testApplicationPropertyOrNullMap() = testApplication {
        environment {
            val mapConfig = MapApplicationConfig()

            mapConfig.put("data.foo", "foo")
            mapConfig.put("data.bar", "bar")

            config = mapConfig
        }

        application {
            val data = property<Map<String, Any?>>("data")
            val dataOrNull = assertDoesNotThrow { propertyOrNull<Map<String, Any?>>("data") }
            val data2 = assertDoesNotThrow { propertyOrNull<Map<String, Any?>>("data2") }

            assertEquals(data, dataOrNull)
            assertEquals(dataOrNull, mapOf("foo" to "foo", "bar" to "bar"))

            assertNull(data2)
        }
    }
}
