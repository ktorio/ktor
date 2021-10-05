import io.ktor.client.plugins.contentnegotiation.tests.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.shared.serializaion.jackson.*
import kotlin.test.*

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class JacksonContentNegotiationTest : JsonContentNegotiationTest(JacksonConverter()) {
    override fun testValidJsonWithExtraFields(): Unit = withTestApplication {
        startServer(application)

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Content-Type", "application/json")
            setBody(""" {"value" : "value", "val" : "bad_json" } """)
        }.let { call ->
            assertEquals(HttpStatusCode.BadRequest, call.response.status())
        }
    }
}
