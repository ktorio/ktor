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

    @Test
    fun testConfigThrowsApplicationConfigurationExceptionForMissingPath() {
        val config = HoconApplicationConfig(ConfigFactory.parseString("ktor { deployment { port = 8080 } }"))

        val topLevel = assertFailsWith<ApplicationConfigurationException> { config.config("nonexistent") }
        assertEquals("Path nonexistent not found.", topLevel.message)
        assertIs<ConfigException.Missing>(topLevel.cause)

        val nested = assertFailsWith<ApplicationConfigurationException> { config.config("ktor.nonexistent") }
        assertEquals("Path ktor.nonexistent not found.", nested.message)

        val fromNestedConfig = assertFailsWith<ApplicationConfigurationException> {
            config.config("ktor").config("deployment.nonexistent")
        }
        assertEquals("Path deployment.nonexistent not found.", fromNestedConfig.message)
        assertIs<ConfigException.Missing>(fromNestedConfig.cause)
    }

    @Test
    fun testConfigListThrowsApplicationConfigurationExceptionForMissingPath() {
        val config = HoconApplicationConfig(ConfigFactory.parseString("ktor { }"))

        val exception = assertFailsWith<ApplicationConfigurationException> { config.configList("ktor.users") }
        assertEquals("Path ktor.users not found.", exception.message)
        assertIs<ConfigException.Missing>(exception.cause)
    }

    @Test
    fun testConfigAndConfigListThrowApplicationConfigurationExceptionForWrongType() {
        val config = HoconApplicationConfig(ConfigFactory.parseString("ktor { deployment { port = 8080 } }"))

        val scalarAsObject = assertFailsWith<ApplicationConfigurationException> {
            config.config("ktor.deployment.port")
        }
        assertIs<ConfigException.WrongType>(scalarAsObject.cause)

        val objectAsList = assertFailsWith<ApplicationConfigurationException> {
            config.configList("ktor.deployment")
        }
        assertIs<ConfigException.WrongType>(objectAsList.cause)
    }

    @Test
    fun testConfigAndConfigListThrowApplicationConfigurationExceptionForInvalidPathExpression() {
        val config = HoconApplicationConfig(ConfigFactory.parseString("ktor { deployment { port = 8080 } }"))

        for (path in listOf("", "ktor.", "ktor..deployment")) {
            val configException = assertFailsWith<ApplicationConfigurationException> { config.config(path) }
            assertTrue(configException.message!!.startsWith("Failed to read path $path:"))

            val configListException = assertFailsWith<ApplicationConfigurationException> { config.configList(path) }
            assertTrue(configListException.message!!.startsWith("Failed to read path $path:"))
        }
    }

    @Test
    fun testConfigAndConfigListAllowEmptyValues() {
        val config = HoconApplicationConfig(
            ConfigFactory.parseString(
                """
                ktor {
                    deployment { }
                    users = []
                }
                """.trimIndent()
            )
        )

        assertEquals(emptySet(), config.config("ktor.deployment").keys())
        assertEquals(emptyList(), config.configList("ktor.users"))
    }

    @Test
    fun testConfigThrowsApplicationConfigurationExceptionForUnresolvedConfig() {
        val unresolved = ConfigFactory.parseString("ktor { deployment = \${base} }\nbase { port = 8080 }")
        val config = HoconApplicationConfig(unresolved)

        val exception = assertFailsWith<ApplicationConfigurationException> { config.config("ktor.deployment") }
        assertIs<ConfigException.NotResolved>(exception.cause)
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
