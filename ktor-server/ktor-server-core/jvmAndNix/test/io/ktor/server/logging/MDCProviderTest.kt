/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.logging

import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlin.test.*

class MDCProviderTest {

    @Test
    fun testLogErrorWithEmptyApplication() = testApplication {
        val environment = createTestEnvironment {  }
        val application = Application(environment)
        assertNotNull(application.mdcProvider)
    }
}
