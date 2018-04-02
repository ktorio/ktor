package io.ktor.tests.server.http

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class DefaultPushTest {
    @Test
    fun testDefaultPush() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                call.push {
                    url.path("push")
                }
            }

            handleRequest(HttpMethod.Get, "/?p=1").let { call ->
                assertEquals("<http://localhost/push?p=1>; rel=prefetch", call.response.headers[HttpHeaders.Link])
            }
        }
    }

    @Test
    fun testMultiPush() {
        withTestApplication {
            application.intercept(ApplicationCallPipeline.Call) {
                call.push {
                    url.path("push")
                }
                call.push {
                    url.path("push2")
                }
            }

            handleRequest(HttpMethod.Get, "/?p=1").let { call ->
                assertEquals("<http://localhost/push?p=1>; rel=prefetch", call.response.headers.values(HttpHeaders.Link)[0])
                assertEquals("<http://localhost/push2?p=1>; rel=prefetch", call.response.headers.values(HttpHeaders.Link)[1])
            }
        }
    }
}