/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.openapi

import io.ktor.server.testing.*
import kotlin.test.*

class OpenAPITest {

    @Test
    fun testResolveOpenAPIFile() = testApplication {
        routing {
            val content = readOpenAPIFile("openapi/documentation.yaml", environment.classLoader)
            assertEquals("hello:world".filter(Char::isLetterOrDigit), content.filter(Char::isLetterOrDigit))
        }
    }
}
