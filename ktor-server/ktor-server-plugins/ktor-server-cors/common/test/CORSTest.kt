/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.plugins.cors.routing.cors
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CORSTest {
    @Test
    fun installedInRouting() = testApplication {
        serverConfig {
            developmentMode = false
        }

        routing {
            cors {
                allowHost("example.com")
                allowMethod(HttpMethod.Put)
            }

            put("test") {
                call.respond("Hello World")
            }
        }

        val response = client.options("/test") {
            headers.append("Origin", "https://example.com")
            headers.append("Access-Control-Request-Method", "PUT")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        response.headers.forEach { name, values ->
            println(name)
        }
        assertEquals(HttpStatusCode.NotFound, client.options("/nonexistent").status)
    }
}
