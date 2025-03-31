/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config.yaml

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import io.ktor.server.config.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlin.test.*

class YamlConfigTest {

    @Test
    fun testYamlApplicationConfig() {
        val content = """
            auth:
                hashAlgorithm: SHA-256
                salt: ktor
                nullable: null
                users:
                    - name: test
                values: 
                    - a
                    - b
                listValues: ['a', 'b', 'c']
        """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        val config = YamlConfig.from(yaml)

        val auth = config.config("auth")
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

        assertEquals(null, config.propertyOrNull("missingProperty"))
        assertEquals(null, config.propertyOrNull("auth.missingProperty"))
        assertEquals("SHA-256", config.propertyOrNull("auth.hashAlgorithm")?.getString())
        assertEquals(listOf("a", "b", "c"), config.propertyOrNull("auth.listValues")?.getList())

        assertEquals(null, config.propertyOrNull("auth.nullable"))
    }

    @Test
    fun testKeysTopLevelYamlConfig() {
        val content = """
            auth:
                hashAlgorithm: SHA-256
                salt: ktor
                users:
                    - name: test
                values: 
                    - a
                    - b
                listValues: ['a', 'b', 'c']
                data:
                    value1: 1
                    value2: 2
        """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        val config = YamlConfig.from(yaml)

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
    fun testKeysNestedYamlConfig() {
        val content = """
            auth:
                nested:
                    data: 
                        value1: 1
                        value2: 2
                    list:
                        - a
                        - b
                data1: 
                    value1: 1
        """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        val config = YamlConfig.from(yaml)

        val nestedConfig = config.config("auth.nested")
        val keys = nestedConfig.keys()
        assertEquals(setOf("data.value1", "data.value2", "list"), keys)
        assertEquals("1", nestedConfig.property("data.value1").getString())
        assertEquals("2", nestedConfig.property("data.value2").getString())
        assertEquals(listOf("a", "b"), nestedConfig.property("list").getList())
    }

    @Test
    fun testEnvironmentVariable() {
        val content = """
            ktor:
                variable: ${'$'}PATH
        """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        val config = YamlConfig.from(yaml)

        val value = config.property("ktor.variable").getString()
        assertTrue(value.isNotEmpty())
        assertFalse(value.contains("PATH"))
    }

    @Test
    fun testMissingEnvironmentVariable() {
        val content = """
            ktor:
                variable: ${'$'}NON_EXISTING_VARIABLE
        """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        assertFailsWith<ApplicationConfigurationException> {
            YamlConfig.from(yaml)
        }
    }

    @Test
    fun testSelfReference() {
        val content = """
            value:
              my: "My value"
            
            config:
              database:
                value: ${'$'}value.my
        """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        val config = YamlConfig.from(yaml)

        assertEquals("My value", config.property("config.database.value").getString())
        assertEquals("My value", config.config("config").property("database.value").getString())
        assertEquals("My value", config.config("config.database").property("value").getString())
    }

    @Test
    fun testSelfReferenceMissing() {
        val content = """
            value:
              my: "My value"
            
            config:
              database:
                value: ${'$'}value.missing
        """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        assertFailsWith<ApplicationConfigurationException> {
            YamlConfig.from(yaml)
        }
    }

    @Test
    fun testMissingEnvironmentVariableWithDefault() {
        val content = """
            ktor:
                variable: "${'$'}NON_EXISTING_VARIABLE:DEFAULT_VALUE"
        """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        val config = YamlConfig.from(yaml)
        assertEquals("DEFAULT_VALUE", config.property("ktor.variable").getString())
    }

    @Test
    fun testOptionalMissingEnvironmentVariable() {
        val content = """
            ktor:
                variable: "${'$'}?NON_EXISTING_VARIABLE"
        """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        val config = YamlConfig.from(yaml)
        assertNull(config.propertyOrNull("ktor.variable"))
    }

    @Test
    fun testOptionalExistingEnvironmentVariable() {
        val content = """
            ktor:
                variable: "${'$'}?PATH"
        """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        val config = YamlConfig.from(yaml)

        val value = config.property("ktor.variable").getString()
        assertTrue(value.isNotEmpty())
        assertFalse(value.contains("PATH"))
    }

    @Test
    fun testExistingEnvironmentVariableWithDefault() {
        val content = """
            ktor:
                variable: "${'$'}PATH:DEFAULT_VALUE"
        """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        val config = YamlConfig.from(yaml)

        val value = config.property("ktor.variable").getString()
        assertTrue(value.isNotEmpty())
        assertFalse(value.contains("PATH"))
        assertFalse(value.contains("DEFAULT_VALUE"))
    }

    @Test
    fun testToMap() {
        val content = """
            hashAlgorithm: SHA-256
            salt: ktor
            users:
                - name: test
                  password: asd
                - name: other
                  password: qwe
            values: 
                - a
                - b
            listValues: ['a', 'b', 'c']
            data:
                value1: 1
                value2: 2
        """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        val config = YamlConfig.from(yaml)

        val map = config.toMap()
        assertEquals(6, map.size)
        assertEquals("SHA-256", map["hashAlgorithm"])
        assertEquals("ktor", map["salt"])
        assertEquals(
            listOf(mapOf("name" to "test", "password" to "asd"), mapOf("name" to "other", "password" to "qwe")),
            map["users"]
        )
        assertEquals(listOf("a", "b"), map["values"])
        assertEquals(listOf("a", "b", "c"), map["listValues"])
        assertEquals(mapOf("value1" to "1", "value2" to "2"), map["data"])
    }

    @Test
    fun readNumber() {
        val content = """
            number: 1234
        """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        val config = YamlConfig.from(yaml)

        assertEquals(1234, config.property("number").getAs())
    }

    @Test
    fun readSerializableClass() {
        val content = """
            auth:
                hashAlgorithm: SHA-256
                salt: ktor
                users:
                    - name: test
                      password: asd
                    - name: other
                      password: qwe
        """.trimIndent()

        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        val config = YamlConfig.from(yaml)

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
