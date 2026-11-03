/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.tests.server.routing

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

// What a call adds to its own pipelines reaches that call, wherever it sends from, and no other
class CallPipelineCopyTest {

    @Test
    fun anInterceptorOnACallsSendPipelineReachesThatCallOnly() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Plugins) {
                if (call.request.queryParameters["mark"] != null) {
                    call.response.pipeline.intercept(ApplicationSendPipeline.After) {
                        call.response.headers.append("X-Marked", "yes")
                    }
                }
            }
        }
        routing {
            get("/") { call.respondText("ok") }
        }

        assertEquals("yes", client.get("/?mark").headers["X-Marked"])
        assertNull(client.get("/").headers["X-Marked"])
        assertEquals("yes", client.get("/?mark").headers["X-Marked"])
    }

    @Test
    fun anInterceptorOnACallsReceivePipelineReachesThatCallOnly() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Plugins) {
                if (call.request.queryParameters["upper"] != null) {
                    call.request.pipeline.intercept(ApplicationReceivePipeline.After) { body ->
                        if (body is String) proceedWith(body.uppercase())
                    }
                }
            }
        }
        routing {
            // receive, not receiveText, which reads the channel without the pipeline
            post("/") { call.respondText(call.receive<String>()) }
        }

        assertEquals("ABC", client.post("/?upper") { setBody("abc") }.bodyAsText())
        assertEquals("abc", client.post("/") { setBody("abc") }.bodyAsText())
    }

    @Test
    fun aRoutePluginsInterceptorReachesAResponseTheEngineCallSendsAfterTheHandlerFailed() = testApplication {
        val marker = createRouteScopedPlugin("marker") {
            onCall { routeCall ->
                // call, in the interceptor, is the call being sent: a leak would mark every later call
                routeCall.response.pipeline.intercept(ApplicationSendPipeline.After) {
                    call.response.headers.append("X-Marked", "yes")
                }
            }
        }
        application {
            intercept(ApplicationCallPipeline.Plugins) {
                try {
                    proceed()
                } catch (_: IllegalStateException) {
                    call.respondText("recovered")
                }
            }
        }
        routing {
            route("/fail") {
                install(marker)
                get { error("the handler failed") }
            }
            get("/ok") { call.respondText("ok") }
        }

        val response = client.get("/fail")
        assertEquals("recovered", response.bodyAsText())
        assertEquals("yes", response.headers["X-Marked"])
        assertNull(client.get("/ok").headers["X-Marked"])
    }
}
