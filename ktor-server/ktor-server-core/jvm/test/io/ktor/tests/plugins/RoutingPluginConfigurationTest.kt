/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.config.ConfigLoader.Companion.load
import io.ktor.server.testing.*
import kotlin.test.*

class RoutingPluginConfigurationTest {

    @Test
    fun testReadPropertyFromFile() {
        var lastInstalledValue = ""
        val plugin = createRouteScopedPlugin("PluginWithProperty", "myplugin", ::ConfigWithProperty) {
            lastInstalledValue = pluginConfig.property
        }

        testApplication {
            environment {
                config = ConfigLoader.load("test-config.yaml")
            }

            install(plugin)
        }

        assertEquals("42", lastInstalledValue)
    }

    @Test
    fun testDefaultValueWithoutConfigFile() {
        var lastInstalledValue = ""
        val plugin = createRouteScopedPlugin("PluginWithProperty", "myplugin", ::ConfigWithProperty) {
            lastInstalledValue = pluginConfig.property
        }

        testApplication {
            install(plugin)
        }
        assertEquals("Default Value", lastInstalledValue)
    }

    @Test
    fun testDefaultValueWithEmptyConfigFile() {
        var lastInstalledValue = ""
        val plugin = createRouteScopedPlugin("PluginWithProperty", "myplugin", ::ConfigWithProperty) {
            lastInstalledValue = pluginConfig.property
        }

        testApplication {
            install(plugin)

            environment {
                config = ConfigLoader.load("empty-config.yaml")
            }
        }

        assertEquals("Default Value", lastInstalledValue)
    }

    @Test
    fun testComplexPath() {
        var lastInstalledValue = ""
        val plugin = createRouteScopedPlugin("PluginWithProperty", "db.myplugin", ::ConfigWithProperty) {
            lastInstalledValue = pluginConfig.property
        }

        testApplication {
            environment {
                config = ConfigLoader.load("test-config.yaml")
            }

            install(plugin)
        }

        assertEquals("43", lastInstalledValue)
    }
}
