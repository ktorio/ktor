package io.ktor.tests.controllers

import io.ktor.controllers.*
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals

@UseExperimental(KtorExperimentalControllersAPI::class)
class PathTest {

    fun <R> withPathTestApplication(test: TestApplicationEngine.() -> R): R
            = withTestApplication({
        install(Controllers)
        routing {
            setupController(ControllerNoPath())
            setupController(ControllerWithPath())
            setupController("setup", ControllerNoPath())
            setupController("setup", ControllerWithPath())
        }
    }, test)

    @RouteController
    class ControllerNoPath {
        @Get("/method") fun method(): String = "nopath"
    }

    @RouteController("controller")
    class ControllerWithPath {
        @Get("/method") fun method(): String = "withpath"
    }

    @Test
    fun shouldCallSimple() {
        withPathTestApplication {
            handleRequest(HttpMethod.Get, "/method").apply {
                assertEquals(200, response.status()?.value)
                assertEquals(response.content, "nopath")
            }
        }
    }

    @Test
    fun shouldCallWithControllerPath() {
        withPathTestApplication {
            handleRequest(HttpMethod.Get, "/controller/method").apply {
                assertEquals(200, response.status()?.value)
                assertEquals(response.content, "withpath")
            }
        }
    }

    @Test
    fun shouldCallWithSetupPath() {
        withPathTestApplication {
            handleRequest(HttpMethod.Get, "/setup/method").apply {
                assertEquals(200, response.status()?.value)
                assertEquals(response.content, "nopath")
            }
        }
    }

    @Test
    fun shouldCallWithDoublePath() {
        withPathTestApplication {
            handleRequest(HttpMethod.Get, "/setup/controller/method").apply {
                assertEquals(200, response.status()?.value)
                assertEquals(response.content, "withpath")
            }
        }
    }
}
