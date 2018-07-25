package io.ktor.webjars

import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import kotlin.test.Test
import kotlin.test.assertEquals


class WebjarsTest {

    @Test
    fun resourceNotFound() {
        withTestApplication {
            application.install(Webjars)
            handleRequest(HttpMethod.Get, "/webjars/foo.js").let { call ->
                assertEquals(HttpStatusCode.NotFound, call.response.status())
           }
        }
    }

    @Test
    fun pathLike() {
        withTestApplication {
            application.install(Webjars)
            application.routing {
                get("/webjars-something/jquery") { call ->

                }
            }
            handleRequest(HttpMethod.Get, "/webjars-something/jquery").let { call ->
                assertEquals(HttpStatusCode.NotFound, call.response.status())
            }
        }
    }

    @Test
    fun nestedPath(){
        withTestApplication {
            application.install(Webjars){
                path = "/assets/webjars"
            }
            handleRequest(HttpMethod.Get, "/assets/webjars/jquery/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun rootPath() {
        withTestApplication {
            application.install(Webjars){
                path = ""
            }
            handleRequest(HttpMethod.Get, "/jquery/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun versionAgnostic() {
        withTestApplication {
            application.install(Webjars)

            handleRequest(HttpMethod.Get, "/webjars/jquery/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

    @Test
    fun withSpecificVersion() {
        withTestApplication {
            application.install(Webjars)

            handleRequest(HttpMethod.Get, "/webjars/jquery/3.3.1/jquery.js").let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("application/javascript", call.response.headers["Content-Type"])
            }
        }
    }

}