/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.hosts

import com.typesafe.config.*
import io.ktor.server.engine.*
import kotlin.test.*

class CommandLineJvmTest {
    @Test
    fun testPropertyConfig() {
        System.setProperty("ktor.deployment.port", "1333")
        ConfigFactory.invalidateCaches()
        assertEquals(1333, commandLineEnvironment(emptyArray()).connectors.single().port)
        System.clearProperty("ktor.deployment.port")
        ConfigFactory.invalidateCaches()
    }

    @Test
    fun testPropertyConfigOverride() {
        System.setProperty("ktor.deployment.port", "1333")
        ConfigFactory.invalidateCaches()
        assertEquals(13698, commandLineEnvironment(arrayOf("-P:ktor.deployment.port=13698")).connectors.single().port)
        System.clearProperty("ktor.deployment.port")
        ConfigFactory.invalidateCaches()
    }
}
