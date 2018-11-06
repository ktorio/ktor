package io.ktor.tests.controllers

import io.ktor.controllers.*
import com.google.gson.Gson
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.controllers.*
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.gson.gson
import io.ktor.http.HttpMethod
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

@UseExperimental(KtorExperimentalControllersAPI::class)
class ResultTest {

    fun <R> withControllerTestApplication(test: TestApplicationEngine.() -> R): R
            = withControllerTestApplication(Controller(), test)

    @RouteController
    class Controller {
        @Get("/nothing") fun getNothing() {}
        @Get("/string") fun getString(): String = "Hello world"
        @Get("/data") fun getData(): Data = Data("Hello world")
        @Get("/null/string") fun getNullString(): String? = null
        @Get("/null/data") fun getNullData(): Data? = null
    }

    data class Data(val value: String)

    @Test
    fun shouldRespondWithNothing() {
        withControllerTestApplication {
            handleRequest(HttpMethod.Get, "/nothing").apply {
                assertNull(response.status())
                assertNull(response.content)
            }
        }
    }

    @Test
    fun shouldRespondWithString() {
        withControllerTestApplication {
            handleRequest(HttpMethod.Get, "/string").apply {
                assertEquals(200, response.status()?.value)
                assertEquals(response.content, "Hello world")
            }
        }
    }

    @Test
    fun shouldRespondWithData() {
        withControllerTestApplication {
            handleRequest(HttpMethod.Get, "/data").apply {
                assertEquals(200, response.status()?.value)
                val resultData = Gson().fromJson<Data>(response.content, Data::class.java)
                assertEquals(resultData, Data("Hello world"))
            }
        }
    }

    @Test
    fun shouldRespondWithNullString() {
        withControllerTestApplication {
            handleRequest(HttpMethod.Get, "/null/string").apply {
                assertNull(response.status())
                assertNull(response.content)
            }
        }
    }

    @Test
    fun shouldRespondWithNullData() {
        withControllerTestApplication {
            handleRequest(HttpMethod.Get, "/null/data").apply {
                assertNull(response.status())
                assertNull(response.content)
            }
        }
    }
}
