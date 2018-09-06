package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.testing.*
import io.ktor.util.pipeline.*
import org.junit.Test
import kotlin.test.*

class CallIdTest {
    @Test
    fun missingFeature(): Unit = withTestApplication {
        handle {
            call.respond(call.callId.toString())
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertTrue { call.requestHandled }
            assertEquals("null", call.response.content)
        }
    }

    @Test
    fun customRetriever(): Unit = withTestApplication {
        application.install(CallId) {
            retrieve { call ->
                call.request.uri
            }
        }
        handle {
            call.respond(call.callId.toString())
        }

        assertEquals("/call-id", handleRequest(HttpMethod.Get, "/call-id").response.content)
    }

    @Test
    fun headerRetriever(): Unit = withTestApplication {
        application.install(CallId) {
            header(HttpHeaders.XRequestId)
        }
        handle {
            call.respond(call.callId.toString())
        }

        handleRequest(HttpMethod.Get, "/") { addHeader(HttpHeaders.XRequestId, "test-id") }.let { call ->
            assertTrue { call.requestHandled }
            assertEquals("test-id", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") {  }.let { call ->
            assertTrue { call.requestHandled }
            assertEquals("null", call.response.content)
        }
    }

    @Test
    fun headerRetrieverWithGenerator(): Unit = withTestApplication {
        application.install(CallId) {
            header(HttpHeaders.XRequestId)
            generate {
                "generated-id"
            }
        }
        handle {
            call.respond(call.callId.toString())
        }

        handleRequest(HttpMethod.Get, "/") { addHeader(HttpHeaders.XRequestId, "test-id") }.let { call ->
            assertTrue { call.requestHandled }
            assertEquals("test-id", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") {  }.let { call ->
            assertTrue { call.requestHandled }
            assertEquals("generated-id", call.response.content)
        }
    }

    @Test
    fun headerRetrieverWithTextGenerator(): Unit = withTestApplication {
        val dictionary = "ABC"
        val length = 64

        application.install(CallId) {
            header(HttpHeaders.XRequestId)
            generate(length, dictionary)
        }
        handle {
            call.respond(call.callId.toString())
        }

        handleRequest(HttpMethod.Get, "/") { addHeader(HttpHeaders.XRequestId, "test-id") }.let { call ->
            assertTrue { call.requestHandled }
            assertEquals("test-id", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") {  }.let { call ->
            assertTrue { call.requestHandled }
            val generatedId = call.response.content!!
            assertNotEquals("null", generatedId)
            assertNotEquals("test-id", generatedId)

            assertEquals(length, generatedId.length)
            assertTrue { dictionary.all { it in generatedId } }
            assertTrue(message = "It should be no non-dictionary characters") { generatedId.toCharArray().none { it !in dictionary } }
        }
    }

    @Test
    fun headerRetrieverWithDefaultTextGenerator(): Unit = withTestApplication {
        val dictionary = CALL_ID_DEFAULT_DICTIONARY
        val length = 64

        application.install(CallId) {
            header(HttpHeaders.XRequestId)
            generate(length = length)
        }
        handle {
            call.respond(call.callId.toString())
        }

        handleRequest(HttpMethod.Get, "/") { addHeader(HttpHeaders.XRequestId, "test-id") }.let { call ->
            assertTrue { call.requestHandled }
            assertEquals("test-id", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") {  }.let { call ->
            assertTrue { call.requestHandled }
            val generatedId = call.response.content!!
            assertNotEquals("null", generatedId)
            assertNotEquals("test-id", generatedId)

            assertEquals(length, generatedId.length)
            assertTrue(message = "It should be no non-dictionary characters") { generatedId.toCharArray().none { it !in dictionary } }
        }
    }

    private fun TestApplicationEngine.handle(block: PipelineInterceptor<Unit, ApplicationCall>) {
        application.intercept(ApplicationCallPipeline.Call, block)
    }
}