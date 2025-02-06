/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import kotlin.test.Test
import kotlin.test.assertTrue

class EnvironmentUtilsTest {

    @Test
    fun testGetAllEnvironment() {
        setEnvironmentProperty("ktor.test", "test")
        val env = getKtorEnvironmentProperties()
        val keys = env.map { it.first }.toSet()

        assertTrue { keys.contains("ktor.test") }
    }
}
