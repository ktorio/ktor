/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.htmx

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.htmx.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.html.body
import kotlinx.html.h1
import kotlin.test.Test
import kotlin.test.assertEquals

class HxRoutingTest {

    @OptIn(ExperimentalKtorApi::class)
    @Test
    fun routing() = testApplication {
        val responseTemplate: (String) -> String = { header ->
            """
                <!DOCTYPE html>
                <html>
                  <body>
                    <h1>$header</h1>
                  </body>
                </html>
            """.trimIndent()
        }

        routing {
            val respondWith: suspend RoutingContext.(String) -> Unit = { text ->
                call.respondHtml {
                    body {
                        h1 { +text }
                    }
                }
            }
            route("htmx") {
                get {
                    call.respondText { "Not HTMX" }
                }
                hx.get {
                    respondWith("No target or trigger")
                }
                hx {
                    target("#test") {
                        get {
                            respondWith("With target")
                        }
                    }
                    trigger("#button") {
                        get {
                            respondWith("With trigger")
                        }
                    }
                }
            }
        }
        assertEquals(
            "Not HTMX",
            client.get("htmx").bodyAsText().trim()
        )
        assertEquals(
            responseTemplate("No target or trigger"),
            client.get("htmx") {
                headers[HxRequestHeaders.Request] = "true"
            }.bodyAsText().trim()
        )
        assertEquals(
            responseTemplate("With target"),
            client.get("htmx") {
                headers[HxRequestHeaders.Request] = "true"
                headers[HxRequestHeaders.Target] = "#test"
            }.bodyAsText().trim()
        )
        assertEquals(
            responseTemplate("With trigger"),
            client.get("htmx") {
                headers[HxRequestHeaders.Request] = "true"
                headers[HxRequestHeaders.Trigger] = "#button"
            }.bodyAsText().trim()
        )
    }
}
