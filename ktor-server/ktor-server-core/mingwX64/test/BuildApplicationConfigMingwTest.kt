/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.server.engine.*
import kotlinx.cinterop.*
import platform.windows.*
import kotlin.test.*

@OptIn(ExperimentalForeignApi::class)
class BuildApplicationConfigMingwTest {
    @Test
    fun testPropertyConfig() = memScoped {
        SetEnvironmentVariable!!("ktor.deployment.port".wcstr.ptr, "1333".wcstr.ptr)
        val config = CommandLineConfig(emptyArray()).engineConfig
        assertEquals(1333, config.connectors.single().port)
        SetEnvironmentVariable!!("ktor.deployment.port".wcstr.ptr, null)
    }

    @Test
    fun testPropertyConfigOverride() = memScoped {
        SetEnvironmentVariable!!("ktor.deployment.port".wcstr.ptr, "1333".wcstr.ptr)
        val config = CommandLineConfig(arrayOf("-P:ktor.deployment.port=13698")).engineConfig
        assertEquals(13698, config.connectors.single().port)
        SetEnvironmentVariable!!("ktor.deployment.port".wcstr.ptr, null)
    }
}
