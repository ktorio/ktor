/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.config

import com.typesafe.config.*
import io.ktor.server.config.*
import kotlinx.serialization.Serializable
import kotlin.test.*

class HoconConfigTest {

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

    @Test
    fun testToMap() {
        val configMap = mapOf(
            "hashAlgorithm" to "SHA-256",
            "salt" to "ktor",
            "users" to listOf(
                mapOf("name" to "test", "password" to "asd"),
                mapOf("name" to "other", "password" to "qwe")
            ),
            "values" to listOf("a", "b"),
            "listValues" to listOf("a", "b", "c"),
            "data" to mapOf("value1" to "1", "value2" to "2"),
        )
        val config = HoconApplicationConfig(ConfigFactory.parseMap(configMap))
        val map = config.toMap()

        assertEquals(configMap, map)
    }

    @Test
    fun readSerializableClass() {
        val content = """
            auth {
                hashAlgorithm = SHA-256
                salt = ktor
                users = [{
                    name = test
                    password = asd
                }, {
                    name = other
                    password = qwe
                }]
            }
        """.trimIndent()

        val config = HoconApplicationConfig(ConfigFactory.parseString(content))

        val securityConfig = config.propertyOrNull("auth")?.getAs<SecurityConfig>()
        assertNotNull(securityConfig)
        assertEquals("SHA-256", securityConfig.hashAlgorithm)
        assertEquals("ktor", securityConfig.salt)
        assertEquals(
            listOf(SecurityUser("test", "asd"), SecurityUser("other", "qwe")),
            securityConfig.users
        )
    }

    @Serializable
    data class SecurityUser(
        val name: String,
        val password: String
    )

    @Serializable
    data class SecurityConfig(
        val hashAlgorithm: String,
        val salt: String,
        val users: List<SecurityUser>,
    )
}
