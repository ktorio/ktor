/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.live.reload

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class LiveReloadTest {

    @Test
    fun injectsRefreshScript() = testApplication {
        install(LiveReload)

        routing {
            get("/") {
                call.respondText(ContentType.Text.Html) {
                    "<html><body><h1>Hello, world!</h1></body></html>"
                }
            }
        }

        client.get("").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(
                "checkForChanges()" in bodyAsText(),
                "checkForChanges() missing from body: ${bodyAsText()}"
            )
        }
    }

}
