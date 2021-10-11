/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.contentnegotiation.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.shared.serialization.*
import kotlin.test.*

abstract class JsonContentNegotiationTest(private val converter: ContentConverter) {
    protected open val extraFieldResult = HttpStatusCode.OK

    data class Wrapper(val value: String)

    fun startServer(testApplicationEngine: Application) {
        testApplicationEngine.install(ContentNegotiation) {
            register(ContentType.Application.Json, converter)
        }

        testApplicationEngine.routing {
            post("/") {
                val wrapper = call.receive<Wrapper>()
                call.respond(wrapper.value)
            }
        }
    }

    @Test
    open fun testBadlyFormattedJson(): Unit = withTestApplication {
        startServer(application)

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Content-Type", "application/json")
            setBody(""" {"value" : "bad_json" """)
        }.let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }
    }

    @Test
    open fun testJsonWithNullValue(): Unit = withTestApplication {
        startServer(application)

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Content-Type", "application/json")
            setBody(""" {"val" : "bad_json" } """)
        }.let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }
    }

    @Test
    open fun testClearValidJson(): Unit = withTestApplication {
        startServer(application)

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Content-Type", "application/json")
            setBody(""" {"value" : "value" }""")
        }.let { call ->
            assertEquals(HttpStatusCode.OK, call.response.status())
        }
    }

    @Test
    open fun testValidJsonWithExtraFields(): Unit = withTestApplication {
        startServer(application)

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Content-Type", "application/json")
            setBody(""" {"value" : "value", "val" : "bad_json" } """)
        }.let { call ->
            assertEquals(extraFieldResult, call.response.status())
        }
    }
}
