package io.ktor.tests.server.routing

import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.*

/**
 * Verifies that the routing feature is not exploring and evaluating more routes than required than required.
 */
class RoutingExploreTest {
    @Test
    fun testSimple() {
        assertEquals(1, getEvaluationCount("/", method = HttpMethod.Get) { get("/") {} })
        assertEquals(2, getEvaluationCount("/", method = HttpMethod.Get) {
            get("/") {}
            post("/") {}
        })
        assertEquals(3, getEvaluationCount("/hello", method = HttpMethod.Get) {
            get("/hello") {}
            get("/world") {}
        })
        assertEquals(4, getEvaluationCount("/hello/world", method = HttpMethod.Get) {
            get("/hello/world") {}
            get("/world/demo") {}
            get("/world/world") {}
            get("/world/hello") {}
            get("/world/nice") {}
            get("/world/other") {}
        })
        assertEquals(4, getEvaluationCount("/hello/world", method = HttpMethod.Get) {
            get("/hello/world") {}
            route("/world") {
                get("/world") {}
                get("/hello") {}
                get("/nice") {}
            }
        })
    }

    fun getEvaluationCount(
        uri: String,
        method: HttpMethod = HttpMethod.Get,
        body: String? = null,
        headers: Headers = headersOf(),
        callback: Routing.() -> Unit
    ): Int {
        var evaluationCount = 0
        withTestApplication {
            application.routing {
                callback()
                Routing.Internal.apply {
                    setEvaluateHook { context, segmentIndex ->
                        evaluationCount++
                        selector.evaluate(context, segmentIndex)
                    }
                }
            }
            handleRequest {
                this.uri = uri
                this.method = method
                if (body != null) this.setBody(body)
                for ((k, v) in headers.entries()) addHeaders(k, v)
            }
        }
        return evaluationCount
    }
}