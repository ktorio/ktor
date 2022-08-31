/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.config.ConfigLoader.Companion.load
import io.ktor.server.testing.*
import kotlin.test.*

class PluginConfigurationTest {

    @Test
    fun testReadPluginPropertyFromFile() {
        var lastInstalledValue = ""
        val plugin = createApplicationPlugin("PluginWithProperty", "myplugin", ::ConfigWithProperty) {
            lastInstalledValue = pluginConfig.property
        }

        testApplication {
            install(plugin)
        }
        assertEquals("Default Value", lastInstalledValue)

        testApplication {
            environment {
                config = ConfigLoader.load("test-config.yaml")
            }
            install(plugin)
        }

        assertEquals("42", lastInstalledValue)
    }
}

internal class ConfigWithProperty(config: ApplicationConfig) {
    var property: String = config.tryGetString("property") ?: "Default Value"
}
