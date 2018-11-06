package io.ktor.tests.controllers

import io.ktor.controllers.*
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.reflect.KParameter
import kotlin.reflect.full.createType
import kotlin.test.assertEquals

@UseExperimental(KtorExperimentalControllersAPI::class)
class CustomParamDetectorTest {

    fun <R> withCustomParamTestApplication(test: TestApplicationEngine.() -> R): R
            = withTestApplication({
        install(Controllers) {
            customParamDetectors += AnswerParamDetector
        }
        routing {
            setupController(Controller())
        }
    }, test)

    @RouteController
    class Controller {
        @Get("/answer") fun getAnswer(answer: TheAnswer): String = answer.value.toString()
    }

    data class TheAnswer(val value: Int)

    object AnswerParamDetector: ParamDetector {
        override fun detect(param: KParameter): ParamMapping? {
            return if (param.type == TheAnswer::class.createType()) {
                { TheAnswer(42) }
            } else {
                null
            }
        }
    }

    @Test
    fun shouldHandleCustomParamDetector() {
        withCustomParamTestApplication {
            handleRequest(HttpMethod.Get, "/answer").apply {
                assertEquals(200, response.status()?.value)
                assertEquals("42", response.content)
            }
        }
    }
}
