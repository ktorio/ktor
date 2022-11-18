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
            val file = resolveOpenAPIFile("openapi/documentation.yaml")
            assertEquals("hello:world".filter(Char::isLetterOrDigit), file.readText().filter(Char::isLetterOrDigit))
        }
    }
}
