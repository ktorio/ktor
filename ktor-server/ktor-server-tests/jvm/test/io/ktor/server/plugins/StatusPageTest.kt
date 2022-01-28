/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.charsets.*
import kotlin.test.*

@Suppress("IMPLICIT_NOTHING_TYPE_ARGUMENT_IN_RETURN_POSITION", "DEPRECATION")
class StatusPageTest {
    private val textHtmlUtf8 = ContentType.Text.Html.withCharset(Charsets.UTF_8)

    @Test
    fun testStatusMapping() {
        withTestApplication {
            application.install(StatusPages) {
                statusFile(HttpStatusCode.NotFound, filePattern = "error#.html")
            }

            application.intercept(ApplicationCallPipeline.Call) {
                call.respond(HttpStatusCode.NotFound)
            }

            handleRequest(HttpMethod.Get, "/foo").let { call ->
                assertEquals(HttpStatusCode.NotFound, call.response.status(), "Actual status must be kept")
                assertEquals("<html><body>error 404</body></html>", call.response.content)
                assertEquals(textHtmlUtf8, call.response.contentType())
            }
        }
    }

    @Test
    fun testStatusMappingWithRoutes() {
        withTestApplication {
            application.install(StatusPages) {
                statusFile(HttpStatusCode.NotFound, filePattern = "error#.html")
            }
            application.routing {
                route("/foo") {
                    route("/wee") {
                        handle {
                            call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                    route("{...}") {
                        handle {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }
            handleRequest(HttpMethod.Get, "/foo").let { call ->
                assertEquals(HttpStatusCode.NotFound, call.response.status())
                assertEquals("<html><body>error 404</body></html>", call.response.content)
            }
            handleRequest(HttpMethod.Get, "/foo/wee").let { call ->
                assertEquals(HttpStatusCode.InternalServerError, call.response.status())
                assertEquals(null, call.response.content)
            }
        }
    }
}
