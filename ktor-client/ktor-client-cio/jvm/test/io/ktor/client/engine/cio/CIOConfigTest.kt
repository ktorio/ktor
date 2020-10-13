/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import kotlin.test.*

class CIOConfigTest {

    @Suppress("DEPRECATION")
    @Test
    fun connectRetryAttemptsShouldDelegateToConnectAttempts() {
        val config = CIOEngineConfig()

        config.endpoint { connectRetryAttempts = 3 }
        assertEquals(3, config.endpoint.connectAttempts)

        config.endpoint { connectAttempts = 5 }
        assertEquals(5, config.endpoint.connectRetryAttempts)
    }
}
