package io.ktor.tests.controllers

import io.ktor.controllers.*
import io.ktor.http.HttpMethod
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.test.assertEquals

@UseExperimental(KtorExperimentalControllersAPI::class)
class FunctionTypeTest {

    fun <R> withControllerTestApplication(test: TestApplicationEngine.() -> R): R
        = withControllerTestApplication(Controller(), test)

    @RouteController
    class Controller {
        @Get("/regular") fun regular(): String = "regular"
        @Get("/suspend") suspend fun suspend(): String {
            return withContext(Dispatchers.IO) {
                "suspend"
            }
        }
    }

    @Test
    fun shouldCallGet() {
        withControllerTestApplication {
            handleRequest(HttpMethod.Get, "/regular").apply {
                assertEquals(200, response.status()?.value)
                assertEquals(response.content, "regular")
            }
        }
    }

    @Test
    fun shouldCallPost() {
        withControllerTestApplication {
            handleRequest(HttpMethod.Get, "/suspend").apply {
                assertEquals(200, response.status()?.value)
                assertEquals(response.content, "suspend")
            }
        }
    }
}
