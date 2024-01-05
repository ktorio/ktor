/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config.yaml

import io.ktor.server.config.*
import kotlin.test.*

@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class YamlConfigTestJvm {

    @Test
    fun testLoadDefaultConfig() {
        val config = YamlConfig(null)!!
        assertEquals("1234", config.property("ktor.deployment.port").getString())
        assertEquals(listOf("a", "b", "c"), config.property("ktor.auth.users").getList())
    }

    @Test
    fun testLoadCustomConfig() {
        val path = YamlConfigTestJvm::class.java.classLoader.getResource("application-custom.yaml").toURI().path
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
}
