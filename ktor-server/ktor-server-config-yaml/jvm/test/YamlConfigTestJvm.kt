/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config.yaml

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import io.ktor.server.config.*
import kotlinx.serialization.decodeFromString
import kotlin.test.*
import kotlin.test.Test

@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class YamlConfigTestJvm {

    @Test
    fun testLoadDefaultConfig() {
        val config = YamlConfig(null)!!
        assertEquals("1234", config.property("ktor.deployment.port").getString())
        assertEquals(listOf("a", "b", "c"), config.property("ktor.auth.users").getList())
    }

    @Test
    fun testLoadCustomConfigWithYamlSuffix() {
        val path = YamlConfigTestJvm::class.java.classLoader.getResource("application-custom.yaml").toURI().path
        val config = YamlConfig(path)!!
        assertEquals("2345", config.property("ktor.deployment.port").getString())
        assertEquals(listOf("c", "d", "e"), config.property("ktor.auth.users").getList())
    }

    @Test
    fun testLoadCustomConfigWithYmlSuffix() {
        val path = YamlConfigTestJvm::class.java.classLoader.getResource("application-custom.yml").toURI().path
        val config = YamlConfig(path)!!
        assertEquals("2345", config.property("ktor.deployment.port").getString())
        assertEquals(listOf("c", "d", "e"), config.property("ktor.auth.users").getList())
    }

    @Test
    fun testLoadWrongConfig() {
        val path = YamlConfigTestJvm::class.java.classLoader.getResource("application-no-env.yaml").toURI().path
        assertFailsWith<ApplicationConfigurationException> {
            YamlConfig(path)
        }
    }

    private fun withProperty(key: String, value: String, block: () -> Unit) {
        val originalValue = System.getProperty(key)
        try {
            System.setProperty(key, value)
            block()
        } finally {
            when (originalValue) {
                null -> System.clearProperty(key)
                else -> System.setProperty(key, originalValue)
            }
        }
    }

    @Test
    fun testSystemPropertyConfig() = withProperty("test.property", "systemValue") {
        val content = $$"""
            ktor:
                property: "$test.property"
            """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        val config = YamlConfig.from(yaml)

        val value = config.property("ktor.property").getString()
        assertEquals("systemValue", value)
    }

    @Test
    fun testEnvVarWithValueStartingWithDollar() = withProperty("TEST_DB_PASSWORD", $$"$123Password") {
        val content = $$"""
                password: $TEST_DB_PASSWORD
            """.trimIndent()
        val yaml = Yaml.default.decodeFromString<YamlMap>(content)
        val config = YamlConfig.from(yaml)
        assertEquals($$"$123Password", config.property("password").getString())
    }
}
