/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config.yaml

import io.ktor.server.config.*
import net.mamoe.yamlkt.*
import kotlin.test.*

class YamlConfigTest {

    @Test
    fun testYamlApplicationConfig() {
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
        """.trimIndent()
        val yaml = Yaml.decodeYamlFromString(content)
        val config = YamlConfig(yaml as YamlMap)

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
        val yaml = Yaml.decodeYamlFromString(content)
        val config = YamlConfig(yaml as YamlMap)

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
        val yaml = Yaml.decodeYamlFromString(content)
        val config = YamlConfig(yaml as YamlMap)

        val nestedConfig = config.config("auth.nested")
        val keys = nestedConfig.keys()
        assertEquals(keys, setOf("data.value1", "data.value2", "list"))
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
        val yaml = Yaml.decodeYamlFromString(content)
        val config = YamlConfig(yaml as YamlMap)

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
        val yaml = Yaml.decodeYamlFromString(content)
        val config = YamlConfig(yaml as YamlMap)
        assertFailsWith<ApplicationConfigurationException> {
            config.checkEnvironmentVariables()
        }
    }
}
