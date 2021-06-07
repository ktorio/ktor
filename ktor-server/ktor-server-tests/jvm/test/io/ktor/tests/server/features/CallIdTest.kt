/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.testing.*
import io.ktor.util.pipeline.*
import kotlin.test.*

class CallIdTest {
    @Test
    fun missingFeature(): Unit = withTestApplication {
        handle {
            call.respond(call.callId.toString())
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
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
            retrieveFromHeader(HttpHeaders.XRequestId)
        }
        handle {
            call.respond(call.callId.toString())
        }

        handleRequest(HttpMethod.Get, "/") { addHeader(HttpHeaders.XRequestId, "test-id") }.let { call ->
            assertEquals("test-id", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") { }.let { call ->
            assertEquals("null", call.response.content)
        }
    }

    @Test
    fun headerRetrieverWithGenerator(): Unit = withTestApplication {
        application.install(CallId) {
            retrieveFromHeader(HttpHeaders.XRequestId)
            generate {
                "generated-id"
            }
        }
        handle {
            call.respond(call.callId.toString())
        }

        handleRequest(HttpMethod.Get, "/") { addHeader(HttpHeaders.XRequestId, "test-id") }.let { call ->
            assertEquals("test-id", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") { }.let { call ->
            assertEquals("generated-id", call.response.content)
        }
    }

    @Test
    fun headerRetrieverWithTextGenerator(): Unit = withTestApplication {
        val dictionary = "abc"
        val length = 64

        application.install(CallId) {
            retrieveFromHeader(HttpHeaders.XRequestId)
            generate(length, dictionary)
        }
        handle {
            call.respond(call.callId.toString())
        }

        handleRequest(HttpMethod.Get, "/") { addHeader(HttpHeaders.XRequestId, "test-id") }.let { call ->
            assertEquals("test-id", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") { }.let { call ->
            val generatedId = call.response.content!!
            assertNotEquals("null", generatedId)
            assertNotEquals("test-id", generatedId)

            assertEquals(length, generatedId.length)
            assertTrue { dictionary.all { it in generatedId } }
            assertTrue(message = "It should be no non-dictionary characters") {
                generatedId.toCharArray().none { it !in dictionary }
            }
        }
    }

    @Test
    fun headerRetrieverWithDefaultTextGenerator(): Unit = withTestApplication {
        val dictionary = CALL_ID_DEFAULT_DICTIONARY
        val length = 64

        application.install(CallId) {
            retrieveFromHeader(HttpHeaders.XRequestId)
            generate(length = length)
        }
        handle {
            call.respond(call.callId.toString())
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.XRequestId, "test-id")
        }.let { call ->
            assertEquals("test-id", call.response.content)
        }

        handleRequest(HttpMethod.Get, "/") { }.let { call ->
            val generatedId = call.response.content!!
            assertNotEquals("null", generatedId)
            assertNotEquals("test-id", generatedId)

            assertEquals(length, generatedId.length)
            assertTrue(message = "It should be no non-dictionary characters") {
                generatedId.toCharArray().none { it !in dictionary }
            }
        }
    }

    @Test
    fun replyToHeader(): Unit = withTestApplication {
        application.install(CallId) {
            header(HttpHeaders.XRequestId)
            generate { "generated-call-id" }
        }
        handle {
            call.respond(call.callId.toString())
        }

        // call id is provided by client
        handleRequest(HttpMethod.Get, "/") { addHeader(HttpHeaders.XRequestId, "client-id") }.let { call ->
            assertEquals("client-id", call.response.content)
            assertEquals("client-id", call.response.headers[HttpHeaders.XRequestId])
        }

        // call id is generated
        handleRequest(HttpMethod.Get, "/").let { call ->
            assertEquals("generated-call-id", call.response.content)
            assertEquals("generated-call-id", call.response.headers[HttpHeaders.XRequestId])
        }
    }

    @Test
    fun testDefaultVerifierForRetrieve(): Unit = withTestApplication {
        application.install(CallId) {
            header(HttpHeaders.XRequestId)
        }
        handle {
            call.respond(call.callId.toString())
        }

        // valid call id
        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.XRequestId, CALL_ID_DEFAULT_DICTIONARY)
        }.let { call ->
            assertEquals(CALL_ID_DEFAULT_DICTIONARY, call.response.content)
            assertEquals(CALL_ID_DEFAULT_DICTIONARY, call.response.headers[HttpHeaders.XRequestId])
        }

        // invalid call id
        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.XRequestId, "\u1000")
        }.let { call ->
            assertEquals("null", call.response.content)
        }
    }

    @Test
    fun testDefaultVerifierForGenerate(): Unit = withTestApplication {
        application.install(CallId) {
            generate { if (it.request.uri == "/valid") CALL_ID_DEFAULT_DICTIONARY else "\u1000" }
        }
        handle {
            call.respond(call.callId.toString())
        }

        // valid call id
        handleRequest(HttpMethod.Get, "/valid").let { call ->
            assertEquals(CALL_ID_DEFAULT_DICTIONARY, call.response.content)
        }

        // invalid call id
        handleRequest(HttpMethod.Get, "/invalid").let { call ->
            assertEquals("null", call.response.content)
        }
    }

    @Test
    fun testCustomVerifier(): Unit = withTestApplication {
        application.install(CallId) {
            header(HttpHeaders.XRequestId)
            verify { if (it.length < 3) throw RejectedCallIdException(it); it.length > 4 }
        }
        handle {
            call.respond(call.callId.toString())
        }

        // valid call id
        handleRequest(HttpMethod.Get, "/") { addHeader(HttpHeaders.XRequestId, "12345") }.let { call ->
            assertEquals("12345", call.response.content)
        }

        // invalid call id that should be ignored
        handleRequest(HttpMethod.Get, "/") { addHeader(HttpHeaders.XRequestId, "123") }.let { call ->
            assertEquals("null", call.response.content)
        }

        // invalid call id that should be rejected with error
        handleRequest(HttpMethod.Get, "/") { addHeader(HttpHeaders.XRequestId, "1") }.let { call ->
            assertEquals(HttpStatusCode.BadRequest.value, call.response.status()?.value)
        }
    }

    private fun TestApplicationEngine.handle(block: PipelineInterceptor<Unit, ApplicationCall>) {
        application.intercept(ApplicationCallPipeline.Call, block)
    }
}
