package io.ktor.tests.controllers

import io.ktor.controllers.*
import io.ktor.application.ApplicationCall
import io.ktor.http.HttpMethod
import io.ktor.response.header
import io.ktor.response.respondText
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import org.junit.Test
import kotlin.test.assertEquals

@UseExperimental(KtorExperimentalControllersAPI::class)
class ParameterTest {

    fun <R> withControllerTestApplication(test: TestApplicationEngine.() -> R): R
        = withControllerTestApplication(Controller(), test)

    @RouteController
    class Controller {
        @Get("/noparams") fun getNoParams(): String = "Hello world!"
        @Get("/call") suspend fun getCall(call: ApplicationCall) {
            call.respondText("Hello world!")
        }

        @Get("/path/{param}") fun getPathParam(@PathParam param: String): String = "Hello $param!"
        @Get("/path/named/{name}") fun getPathParamNamed(@PathParam("name") param: String): String = "Hello $param!"
        @Get("/path/{param}/nullable") fun getPathParamNullable(@PathParam param: String?): String = "Hello $param!"

        @Get("/query") fun getQueryParam(@QueryParam param: String): String = "Hello $param!"
        @Get("/query/named") fun getQueryParamNamed(@QueryParam("name") param: String): String = "Hello $param!"
        @Get("/query/nullable") fun getQueryParamNullable(@QueryParam param: String?): String = "Hello $param!"

        @Get("/multiple/{pathParam}")
        fun getMultipleParams(call: ApplicationCall, @PathParam pathParam: String?, @QueryParam query: String): String {
            call.response.header("header", "value")
            return "$pathParam $query!"
        }
    }

    private fun baseParamTest(url: String, responseContent: String = "Hello world!", test: (TestApplicationCall.() -> Unit)? = null) {
        withControllerTestApplication {
            handleRequest(HttpMethod.Get, url).apply {
                assertEquals(200, response.status()?.value)
                assertEquals(responseContent, response.content)
                test?.invoke(this)
            }
        }
    }

    @Test fun shouldMapNoParams() = baseParamTest("/noparams")

    @Test fun shouldMapCallParam() = baseParamTest("/call")

    @Test fun shouldMapPathParam() = baseParamTest("/path/world")
    @Test fun shouldMapPathParamWithExplicitName() = baseParamTest("/path/named/world")
    @Test fun shouldMapNullablePathParam() = baseParamTest("/path/world/nullable")

    @Test fun shouldMapQueryParam() = baseParamTest("/query?param=world")
    @Test fun shouldMapQueryParamWithExplicitName() = baseParamTest("/query/named?name=world")
    @Test fun shouldMapNullableQueryParam() = baseParamTest("/query/nullable?param=world")

    @Test fun shouldMapMultipleParams() = baseParamTest("/multiple/Hello?query=world") {
        assertEquals("value", response.headers.get("header"))
    }
}
